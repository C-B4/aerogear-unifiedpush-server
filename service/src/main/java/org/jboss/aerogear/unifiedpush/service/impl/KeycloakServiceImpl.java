package org.jboss.aerogear.unifiedpush.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.ejb.Asynchronous;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.DependsOn;
import javax.ejb.Singleton;
import javax.ejb.Startup;

import org.apache.commons.lang3.StringUtils;
import org.jboss.aerogear.unifiedpush.api.Alias;
import org.jboss.aerogear.unifiedpush.api.PushApplication;
import org.jboss.aerogear.unifiedpush.api.Variant;
import org.jboss.aerogear.unifiedpush.service.KeycloakService;
import org.jboss.aerogear.unifiedpush.service.OAuth2ConfigurationBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Startup
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
@DependsOn(value = { "ConfigurationServiceImpl" })
/*
 * TODO - Convert to Spring bean. 1 - Add Spring caching to
 * getVariantIdsFromClient 2 - Replace ejb async with spring. 3 - Support remove
 * client API.
 */
public class KeycloakServiceImpl implements KeycloakService {
	private static final Logger logger = LoggerFactory.getLogger(KeycloakServiceImpl.class);

	private static final String CLIENT_PREFIX = "ups-installation-";
	private static final String KEYCLOAK_ROLE_USER = "installation";
	private static final String SUBDOMAIN_SEPERATOR = "-";
	private static final String UPDATE_PASSWORD_ACTION = "UPDATE_PASSWORD";

	private static final String ATTRIBUTE_VARIANT_SUFFIX = "_variantid";
	private static final String ATTRIBUTE_SECRET_SUFFIX = "_secret";

	private volatile Boolean oauth2Enabled;
	private Keycloak kc;
	private RealmResource realm;

	public boolean isInitialized() {
		if (!OAuth2ConfigurationBuilder.isOAuth2Enabled()) {
			return false;
		}

		if (oauth2Enabled == null) {
			synchronized (this) {
				if (oauth2Enabled == null) {

					if (OAuth2ConfigurationBuilder.isOAuth2Enabled()) {
						this.initialize();
					}

					oauth2Enabled = OAuth2ConfigurationBuilder.isOAuth2Enabled();
				}
			}
		}

		return oauth2Enabled.booleanValue();
	}

	private void initialize() {
		String keycloakPath = OAuth2ConfigurationBuilder.getOAuth2Url();

		String upsiRealmId = OAuth2ConfigurationBuilder.getUpsiRealm();
		String cliClientId = OAuth2ConfigurationBuilder.getAdminClient();
		String userName = OAuth2ConfigurationBuilder.getAdminUserName();
		String userPassword = OAuth2ConfigurationBuilder.getAdminPassword();

		this.kc = KeycloakBuilder.builder() //
				.serverUrl(keycloakPath) //
				.realm(upsiRealmId)//
				.username(userName) //
				.password(userPassword) //
				.clientId(cliClientId) //
				.resteasyClient( //
						// Setting TTL to 10 seconds, prevent KC token
						// expiration.
						new ResteasyClientBuilder().connectionPoolSize(25).connectionTTL(10, TimeUnit.SECONDS).build()) //
				.build();

		this.realm = this.kc.realm(upsiRealmId);

		setRealmConfiguration();
	}

	private void setRealmConfiguration() {
		RealmRepresentation realmRepresentation = this.realm.toRepresentation();
		realmRepresentation.setRememberMe(true);
		realmRepresentation.setResetPasswordAllowed(true);
	}

	@Override
	public void createClientIfAbsent(PushApplication pushApplication) {
		if (!isInitialized()) {
			return;
		}

		String simpleApplicationName = pushApplication.getName().toLowerCase();
		String applicationName = CLIENT_PREFIX + simpleApplicationName;
		ClientRepresentation clientRepresentation = isClientExists(pushApplication);

		if (this.oauth2Enabled && clientRepresentation == null) {
			clientRepresentation = new ClientRepresentation();

			clientRepresentation.setId(applicationName);
			clientRepresentation.setClientId(applicationName);
			clientRepresentation.setEnabled(true);

			String domain = OAuth2ConfigurationBuilder.getRooturlDomain();
			String protocol = OAuth2ConfigurationBuilder.getRooturlProtocol();
			clientRepresentation.setRootUrl(protocol + "://" + simpleApplicationName + SUBDOMAIN_SEPERATOR + domain);
			clientRepresentation.setRedirectUris(Arrays.asList("/*"));
			clientRepresentation.setBaseUrl("/");

			clientRepresentation.setStandardFlowEnabled(true);
			clientRepresentation.setPublicClient(true);
			clientRepresentation.setWebOrigins(Arrays.asList("*"));

			clientRepresentation.setAttributes(getClientAttributes(pushApplication));
			this.realm.clients().create(clientRepresentation);
		} else {
			ClientResource clientResource = this.realm.clients().get(clientRepresentation.getId());
			clientRepresentation.setAttributes(getClientAttributes(pushApplication));
			clientResource.update(clientRepresentation);
		}
	}

	@Asynchronous
	public void createUserIfAbsent(String userName) {
		if (!isInitialized()) {
			return;
		}

		UserRepresentation user = getUser(userName);

		if (user == null) {
			user = new UserRepresentation();
			user.setUsername(userName);

			user.setRequiredActions(Arrays.asList(UPDATE_PASSWORD_ACTION));

			user.setRealmRoles(Collections.singletonList(KEYCLOAK_ROLE_USER));

			user.setEnabled(false);

			this.realm.users().create(user);
		} else {
			logger.debug("KC Username {}, already exist", userName);
		}
	}

	@Asynchronous
	public void disable(Alias alias) {
		if (!isInitialized()) {
			return;
		}

		updateUser(alias.getEmail(), null, false);
	}

	@Asynchronous
	public void delete(String userName) {
		if (!isInitialized()) {
			return;
		}

		UserRepresentation user = getUser(userName);
		if (user == null) {
			logger.debug(String.format("Unable to find user %s, in keyclock", userName));
			return;
		}

		this.realm.users().delete(user.getId());
	}

	@Override
	/*
	 * Update user must be done synchronously. When user tries to verify code,
	 * and response is immediate (asynchronously), client might try to
	 * authenticate before KC operation is complete.
	 */
	public void updateUser(String userName, String password) {
		if (!isInitialized()) {
			return;
		}

		createUserIfAbsent(userName);
		updateUser(userName, password, true);
	}

	@Override
	public List<String> getVariantIdsFromClient(String clientId) {
		if (!isInitialized()) {
			return null;
		}

		ClientRepresentation client = isClientExists(clientId);

		List<String> variantIds = null;
		if (client != null) {
			Map<String, String> attributes = client.getAttributes();
			if (attributes != null) {
				variantIds = new ArrayList<String>(attributes.size());
				for (Map.Entry<String, String> entry : attributes.entrySet()) {
					if (entry.getKey().endsWith(ATTRIBUTE_VARIANT_SUFFIX)) {
						variantIds.add(entry.getValue());
					}
				}
			}
		}

		return variantIds;
	}

	@Override
	public void updateUserPassword(String aliasId, String currentPassword, String newPassword) {
		UserRepresentation user = getUser(aliasId);
		if (user == null) {
			logger.debug(String.format("Unable to find user %s, in keyclock", aliasId));
			return;
		}

		boolean isCurrentPasswordValid = isCurrentPasswordValid(user, currentPassword);

		if (isCurrentPasswordValid == true) {
			UsersResource users = this.realm.users();
			UserResource userResource = users.get(user.getId());
			updateUserPassword(userResource, newPassword);
		}
	}

	private boolean isCurrentPasswordValid(UserRepresentation user, String currentPassword) {
		// TODO: add current password validations
		return true;
	}

	private void updateUser(String userName, String password, boolean enable) {
		UserRepresentation user = getUser(userName);
		if (user == null) {
			logger.debug(String.format("Unable to find user %s, in keyclock", userName));
			return;
		}

		UserResource userResource = this.realm.users().get(user.getId());

		user.setEnabled(enable);

		if (StringUtils.isNotEmpty(password)) {
			user.setEmailVerified(true);
			user.setEmail(userName);

			updateUserPassword(userResource, password);
		}

		userResource.update(user);
	}

	private void updateUserPassword(UserResource userResource, String password) {
		CredentialRepresentation credential = new CredentialRepresentation();
		credential.setType(CredentialRepresentation.PASSWORD);
		credential.setValue(password);

		userResource.resetPassword(credential);
	}

	private UserRepresentation getUser(String username) {
		List<UserRepresentation> users = this.realm.users().search(username, 0, 1);
		if (users != null && users.size() > 0) {
			return users.get(0);
		}

		return null;
	}

	private ClientRepresentation isClientExists(PushApplication pushApp) {
		return isClientExists(CLIENT_PREFIX + pushApp.getName().toLowerCase());
	}

	private ClientRepresentation isClientExists(String clientId) {
		List<ClientRepresentation> clients = this.realm.clients().findByClientId(clientId);

		if (clients == null | clients.size() == 0) {
			return null;
		}

		// Return first client
		return clients.get(0);
	}

	private Map<String, String> getClientAttributes(PushApplication pushApp) {
		List<Variant> variants = pushApp.getVariants();
		Map<String, String> attributes = new HashMap<>(variants.size());
		for (Variant variant : variants) {
			String varName = variant.getName().toLowerCase();
			attributes.put(varName + ATTRIBUTE_VARIANT_SUFFIX, variant.getVariantID());
			attributes.put(varName + ATTRIBUTE_SECRET_SUFFIX, variant.getSecret());
		}

		return attributes;
	}
}