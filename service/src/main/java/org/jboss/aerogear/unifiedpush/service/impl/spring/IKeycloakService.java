package org.jboss.aerogear.unifiedpush.service.impl.spring;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jboss.aerogear.unifiedpush.api.PushApplication;
import org.jboss.aerogear.unifiedpush.service.impl.UserIdentifiers;
import org.springframework.cache.annotation.Cacheable;

public interface IKeycloakService {
	public static final String CACHE_NAME = "variant-ids-per-clientid";

	void createClientIfAbsent(PushApplication pushApplication);

	void removeClient(PushApplication pushApplicaiton);

	void createVerifiedUserIfAbsent(String userName, String password, UserIdentifiers userIdentifiers);

	boolean exists(String userName);

	void delete(String userName);

	@Cacheable(value = IKeycloakService.CACHE_NAME, unless = "#result == null")
	List<String> getVariantIdsFromClient(String clientId);

	void updateUserPassword(String aliasId, String currentPassword, String newPassword);

	void resetUserPassword(String aliasId, String newPassword);

	boolean isInitialized();

	String strip(String fqdn);

	String seperator();

	int updateUserAttribute(Map<String, ? extends Collection<UserIdentifiers>> aliasToIdentifiers);
}
