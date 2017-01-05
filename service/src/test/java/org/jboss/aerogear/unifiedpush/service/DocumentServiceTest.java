package org.jboss.aerogear.unifiedpush.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.jboss.aerogear.unifiedpush.api.Alias;
import org.jboss.aerogear.unifiedpush.api.DocumentMetadata;
import org.jboss.aerogear.unifiedpush.api.Installation;
import org.jboss.aerogear.unifiedpush.api.InstallationVerificationAttempt;
import org.jboss.aerogear.unifiedpush.api.PushApplication;
import org.jboss.aerogear.unifiedpush.api.Variant;
import org.jboss.aerogear.unifiedpush.document.MessagePayload;
import org.jboss.aerogear.unifiedpush.message.Criteria;
import org.jboss.aerogear.unifiedpush.message.UnifiedPushMessage;
import org.jboss.aerogear.unifiedpush.service.VerificationService.VerificationResult;
import org.jboss.aerogear.unifiedpush.system.ConfigurationEnvironment;
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.datastax.driver.core.utils.UUIDs;

public class DocumentServiceTest extends AbstractBaseServiceTest {
	private static final String DEFAULT_DEVICE_TOKEN = "c5106a4e97ecc8b8ab8448c2ebccbfa25938c0f9a631f96eb2dd5f16f0bedc40";
	private static final String DEFAULT_DEVICE_ALIAS = "17327572923";
	private static final String DEFAULT_DEVICE_DATABASE = "TEST";

	private static final String DEFAULT_VARIENT_ID = "d3f54c25-c3ce-4999-b7a8-27dc9bb01364";

	@Inject
	private DocumentService documentService;
	@Inject
	private GenericVariantService genericVariantService;
	@Inject
	private ClientInstallationService installationService;
	@Inject
	private PushApplicationService applicationService;
	@Inject
	private VerificationService verificationService;
	@Inject
	private AliasService aliasService;

	@Before
	public void cleanup() {
		System.clearProperty(ConfigurationEnvironment.PROP_ENABLE_VERIFICATION);
	}

	@Override
	protected void specificSetup() {

	}

	@Test
	@Transactional(TransactionMode.ROLLBACK)
	public void saveDocumentTest() {
		System.setProperty(ConfigurationEnvironment.PROP_ENABLE_VERIFICATION, "true");

		// Prepare installation
		Installation iosInstallation = new Installation();
		iosInstallation.setDeviceType("iPhone7,2");
		iosInstallation.setDeviceToken(DEFAULT_DEVICE_TOKEN);
		iosInstallation.setOperatingSystem("iOS");
		iosInstallation.setOsVersion("9.0.2");
		iosInstallation.setAlias(DEFAULT_DEVICE_ALIAS);

		try {
			Variant variant = genericVariantService.findByVariantID(DEFAULT_VARIENT_ID);
			Assert.assertTrue(variant.getVariantID().equals(DEFAULT_VARIENT_ID));

			installationService.addInstallationSynchronously(variant, iosInstallation);

			Installation inst = installationService.findById(iosInstallation.getId());
			Assert.assertTrue(inst != null && inst.isEnabled() == false);

			// Register alias
			PushApplication pushApplication = applicationService.findByVariantID(DEFAULT_VARIENT_ID);

			// Save alias should return without saving, device is not enabled.
			MessagePayload msgPayload = new MessagePayload(UnifiedPushMessage.withAlias(DEFAULT_DEVICE_ALIAS),
					"{TEST JSON}", DEFAULT_DEVICE_DATABASE, DocumentMetadata.NULL_ID);
			documentService.save(pushApplication, msgPayload, false);
			String document = documentService.getLatestFromAlias(pushApplication, DEFAULT_DEVICE_ALIAS,
					DEFAULT_DEVICE_DATABASE, DocumentMetadata.NULL_ID);

			// Enable device
			String code = verificationService.initiateDeviceVerification(inst, variant);
			VerificationResult results = verificationService.verifyDevice(inst, variant,
					new InstallationVerificationAttempt(code, inst.getDeviceToken()));
			Assert.assertTrue(results != null && results.equals(VerificationResult.SUCCESS));

			// Re-save device
			documentService.save(pushApplication, msgPayload, false);
			document = documentService.getLatestFromAlias(pushApplication, DEFAULT_DEVICE_ALIAS,
					DEFAULT_DEVICE_DATABASE, DocumentMetadata.NULL_ID);

			Assert.assertTrue(document != null && document.equals("{TEST JSON}"));
		} catch (Throwable e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	@Transactional(TransactionMode.ROLLBACK)
	public void lastUpdatedDocumentTest() {
		saveDocumentTest();

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// Nothing to do
		}

		PushApplication pushApplication = applicationService.findByVariantID(DEFAULT_VARIENT_ID);

		MessagePayload msgPayload = new MessagePayload(UnifiedPushMessage.withAlias(DEFAULT_DEVICE_ALIAS),
				"{TEST JSON NEWEST}", DEFAULT_DEVICE_DATABASE, DocumentMetadata.NULL_ID);

		// Save alias should return without saving, device is not enabled.
		documentService.save(pushApplication, msgPayload, false);
		String document = documentService.getLatestFromAlias(pushApplication, DEFAULT_DEVICE_ALIAS,
				DEFAULT_DEVICE_DATABASE, DocumentMetadata.NULL_ID);

		Assert.assertTrue(document != null && document.equals("{TEST JSON NEWEST}"));
	}

	@Test
	@Transactional(TransactionMode.ROLLBACK)
	public void saveGlobalDocumentTest() {
		PushApplication pushApplication = applicationService.findByVariantID(DEFAULT_VARIENT_ID);

		MessagePayload msgPayload = new MessagePayload(
				UnifiedPushMessage.withAlias(DocumentMetadata.NULL_ALIAS.toString()), "{TEST JSON NULL_ALIAS}",
				DEFAULT_DEVICE_DATABASE, DocumentMetadata.NULL_ID);

		// Save alias should return without saving, device is not enabled.
		documentService.save(pushApplication, msgPayload, false);
		String document = documentService.getLatestFromAlias(pushApplication, DocumentMetadata.NULL_ALIAS.toString(),
				DEFAULT_DEVICE_DATABASE, DocumentMetadata.NULL_ID);

		Assert.assertTrue(document != null && document.equals("{TEST JSON NULL_ALIAS}"));
	}

	@Test
	@Transactional(TransactionMode.ROLLBACK)
	public void saveDocumentWithoutVerificationModeTest() {
		// Prepare installation
		Installation iosInstallation = new Installation();
		iosInstallation.setDeviceType("iPhone7,2");
		iosInstallation.setDeviceToken(DEFAULT_DEVICE_TOKEN);
		iosInstallation.setOperatingSystem("iOS");
		iosInstallation.setOsVersion("9.0.2");
		iosInstallation.setAlias(DEFAULT_DEVICE_ALIAS);

		Variant variant = genericVariantService.findByVariantID(DEFAULT_VARIENT_ID);
		Assert.assertTrue(variant.getVariantID().equals(DEFAULT_VARIENT_ID));

		installationService.addInstallationSynchronously(variant, iosInstallation);

		Installation inst = installationService.findById(iosInstallation.getId());
		Assert.assertTrue(inst != null && inst.isEnabled() == true);

		// Register alias
		PushApplication pushApplication = applicationService.findByVariantID(variant.getVariantID());

		MessagePayload msgPayload = new MessagePayload(UnifiedPushMessage.withAlias(DEFAULT_DEVICE_ALIAS),
				"{TEST JSON}", DEFAULT_DEVICE_DATABASE, DocumentMetadata.NULL_ID);

		// Save alias should return without saving, divice is not emabled.
		documentService.save(pushApplication, msgPayload, false);
		String document = documentService.getLatestFromAlias(pushApplication, DEFAULT_DEVICE_ALIAS,
				DEFAULT_DEVICE_DATABASE, DocumentMetadata.NULL_ID);

		Assert.assertTrue(document != null && document.equals("{TEST JSON}"));
	}

	@Test
	@Transactional(TransactionMode.ROLLBACK)
	public void saveDocumentOverwriteTest() {
		// Prepare installation
		Installation iosInstallation = new Installation();
		iosInstallation.setDeviceType("iPhone7,2");
		iosInstallation.setDeviceToken(DEFAULT_DEVICE_TOKEN);
		iosInstallation.setOperatingSystem("iOS");
		iosInstallation.setOsVersion("9.0.2");
		iosInstallation.setAlias(DEFAULT_DEVICE_ALIAS);

		Variant variant = genericVariantService.findByVariantID(DEFAULT_VARIENT_ID);
		Assert.assertTrue(variant.getVariantID().equals(DEFAULT_VARIENT_ID));

		installationService.addInstallationSynchronously(variant, iosInstallation);

		Installation inst = installationService.findById(iosInstallation.getId());
		Assert.assertTrue(inst != null && inst.isEnabled());

		// Register alias
		PushApplication pushApplication = applicationService.findByVariantID(variant.getVariantID());

		MessagePayload msgPayload = new MessagePayload(UnifiedPushMessage.withAlias(DEFAULT_DEVICE_ALIAS),
				"{TEST JSON}", DEFAULT_DEVICE_DATABASE, DocumentMetadata.NULL_ID);

		// Save once
		documentService.save(pushApplication, msgPayload, true);
		String document = documentService.getLatestFromAlias(pushApplication, DEFAULT_DEVICE_ALIAS,
				DEFAULT_DEVICE_DATABASE, DocumentMetadata.NULL_ID);

		Assert.assertTrue(document != null && document.equals("{TEST JSON}"));

		msgPayload.setPayload("{TEST JSON 2}");
		// save 2nd time and check that it was overwritten
		documentService.save(pushApplication, msgPayload, true);
		document = documentService.getLatestFromAlias(pushApplication, DEFAULT_DEVICE_ALIAS, DEFAULT_DEVICE_DATABASE,
				DocumentMetadata.NULL_ID);

		Assert.assertTrue(document != null && document.equals("{TEST JSON 2}"));
	}

	@Test
	@Transactional(TransactionMode.ROLLBACK)
	public void testFindLatestDocumentsForApplication() {
		Variant variant = genericVariantService.findByVariantID(DEFAULT_VARIENT_ID);
		PushApplication pushApp = applicationService.findByVariantID(variant.getVariantID());

		String alias1 = "alias1";
		String alias2 = "alias2";

		aliasService.create(new Alias(UUID.fromString(pushApp.getPushApplicationID()), UUIDs.timeBased(), alias1));
		aliasService.create(new Alias(UUID.fromString(pushApp.getPushApplicationID()), UUIDs.timeBased(), alias2));

		documentService.save(pushApp, alias1, "doc1", DEFAULT_DEVICE_DATABASE, "test_id", true);
		documentService.save(pushApp, alias2, "doc2", DEFAULT_DEVICE_DATABASE, "test_id", true);

		List<String> docs = documentService.getLatestFromAliases(pushApp, DEFAULT_DEVICE_DATABASE, "test_id");
		Assert.assertEquals(new HashSet<>(docs), new HashSet<>(Arrays.asList("doc1", "doc2")));
	}

	@Test
	@Transactional(TransactionMode.ROLLBACK)
	public void testNullAliasDocument() {
		Variant variant = genericVariantService.findByVariantID(DEFAULT_VARIENT_ID);
		PushApplication pushApp = applicationService.findByVariantID(variant.getVariantID());

		UnifiedPushMessage message = new UnifiedPushMessage();
		message.setCriteria(new Criteria());
		message.getCriteria().setAliases(new ArrayList<>());
		message.getCriteria().getAliases().add(null);
		MessagePayload payload = new MessagePayload(message, "{TEST PAYLOAD}");

		documentService.save(pushApp, payload, true);

		String latest = documentService.getLatestFromAlias(pushApp, null, DocumentMetadata.NULL_DATABASE,
				DocumentMetadata.NULL_ID);
		Assert.assertTrue(latest != null && latest.equals("{TEST PAYLOAD}"));
	}

	@Test
	@Transactional(TransactionMode.ROLLBACK)
	public void testBadPhoneNumberDocument() {
		Variant variant = genericVariantService.findByVariantID(DEFAULT_VARIENT_ID);
		PushApplication pushApp = applicationService.findByVariantID(variant.getVariantID());

		String alias1 = "9720525679037170105113810";
		String alias2 = "9720521550826170105113810";

		// Remove (Disable) 'p1' alias from aliases list.
		aliasService.updateAliasesAndInstallations(pushApp, Arrays.asList(alias1, alias2), false);

		documentService.save(pushApp, alias1, "doc1", DEFAULT_DEVICE_DATABASE, "test_id", true);
		documentService.save(pushApp, alias2, "doc2", DEFAULT_DEVICE_DATABASE, "test_id", true);

		List<String> docs = documentService.getLatestFromAliases(pushApp, DEFAULT_DEVICE_DATABASE, "test_id");
		Assert.assertEquals(new HashSet<>(docs), new HashSet<>(Arrays.asList("doc1", "doc2")));
	}
}
