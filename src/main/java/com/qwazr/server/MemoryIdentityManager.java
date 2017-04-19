/**
 * s * Copyright 2017 Emmanuel Keller / QWAZR
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qwazr.server;

import com.qwazr.utils.CharsetUtils;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.DigestCredential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.util.HexConverter;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.auth.UsernamePasswordCredentials;

import java.security.MessageDigest;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryIdentityManager implements IdentityManager {

	private final ConcurrentHashMap<String, UserAccount> accounts = new ConcurrentHashMap<>();

	public void addBasic(String id, String name, String password, String... roles) {
		accounts.put(id, new BasicAccount(name, password, roles));
	}

	public void addDigest(String realm, String id, String name, String password, String... roles) {
		accounts.put(id, new DigestAccount(realm, name, password, roles));
	}

	@Override
	public Account verify(Account account) {
		return account;
	}

	@Override
	public Account verify(String id, Credential credential) {
		return accounts.get(id).check(credential);
	}

	@Override
	public Account verify(Credential credential) {
		if (credential instanceof UsernamePasswordCredentials) {
			final UsernamePasswordCredentials usernamePasswordCredentials = (UsernamePasswordCredentials) credential;
			return verify(usernamePasswordCredentials.getUserName(), credential);
		}
		return null;
	}

	private static abstract class UserAccount implements Account, Principal {

		private final String name;
		private final Set<String> roles;

		protected UserAccount(String name, String... roles) {
			this.name = name;
			if (roles == null || roles.length == 0)
				this.roles = Collections.emptySet();
			else {
				this.roles = new LinkedHashSet<>();
				Collections.addAll(this.roles, roles);
			}
		}

		@Override
		public Principal getPrincipal() {
			return this;
		}

		@Override
		public Set<String> getRoles() {
			return roles;
		}

		@Override
		public String getName() {
			return name;
		}

		protected abstract Account check(final Credential credential);
	}

	private static class BasicAccount extends UserAccount {

		private final char[] password;

		private BasicAccount(String name, String password, String... roles) {
			super(name, roles);
			this.password = password.toCharArray();
		}

		@Override
		protected Account check(final Credential credential) {
			if (!(credential instanceof PasswordCredential))
				return null;
			final PasswordCredential passwordCredential = (PasswordCredential) credential;
			return Arrays.equals(password, passwordCredential.getPassword()) ? this : null;
		}
	}

	private static class DigestAccount extends UserAccount {

		private final byte[] digestPassword;

		private DigestAccount(String realm, String name, String password, String... roles) {
			super(name, roles);
			final MessageDigest digest = DigestUtils.getMd5Digest();
			try {
				digest.update(getPrincipal().getName().getBytes(CharsetUtils.CharsetUTF8));
				digest.update((byte) ':');
				digest.update(realm.getBytes(CharsetUtils.CharsetUTF8));
				digest.update((byte) ':');
				digest.update(password.getBytes(CharsetUtils.CharsetUTF8));
				digestPassword = HexConverter.convertToHexBytes(digest.digest());
			} finally {
				digest.reset();
			}
		}

		@Override
		protected Account check(final Credential credential) {
			if (!(credential instanceof DigestCredential))
				return null;
			final DigestCredential digestCredential = (DigestCredential) credential;
			return digestCredential.verifyHA1(digestPassword) ? this : null;
		}
	}

}