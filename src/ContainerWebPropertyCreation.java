/**
 * Access and manage a Google Tag Manager account.
 */

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.analytics.Analytics;
import com.google.api.services.analytics.AnalyticsScopes;
import com.google.api.services.analytics.model.Account;
import com.google.api.services.analytics.model.Accounts;
import com.google.api.services.analytics.model.EntityUserLink;
import com.google.api.services.analytics.model.EntityUserLink.Permissions;
import com.google.api.services.analytics.model.Profile;
import com.google.api.services.analytics.model.UserRef;
import com.google.api.services.analytics.model.Webproperty;
import com.google.api.services.tagmanager.TagManager;
import com.google.api.services.tagmanager.TagManagerScopes;
import com.google.api.services.tagmanager.model.AccountAccess;
import com.google.api.services.tagmanager.model.Condition;
import com.google.api.services.tagmanager.model.Container;
import com.google.api.services.tagmanager.model.ContainerAccess;
import com.google.api.services.tagmanager.model.CreateContainerVersionRequestVersionOptions;
import com.google.api.services.tagmanager.model.CreateContainerVersionResponse;
import com.google.api.services.tagmanager.model.ListAccountUsersResponse;
import com.google.api.services.tagmanager.model.ListAccountsResponse;
import com.google.api.services.tagmanager.model.ListMacrosResponse;
import com.google.api.services.tagmanager.model.Macro;
import com.google.api.services.tagmanager.model.Parameter;
import com.google.api.services.tagmanager.model.Rule;
import com.google.api.services.tagmanager.model.Tag;
import com.google.api.services.tagmanager.model.UserAccess;

public class ContainerWebPropertyCreation {
	private static final String GTM_ALCON_CONTAINER_ID 	= "958213";
	private static final String GTM_ALCON_ACCOUNT_ID 	= "36837426";
	private static final String GTM_CONTAINER_PREFIX 	= "GTM ";
	private static final String GA_CONTAINER_PREFIX 	= "GA ";

	// Path to client_secrets.json file downloaded from the Developer's Console.
	private static final String CLIENT_SECRET_JSON_RESOURCE = "client_secrets.json";

	// The directory where the user's credentials will be stored for the
	// application.
	private static final File DATA_STORE_DIR = new File("./credentials");

	private static final String APPLICATION_NAME = "ews-gtm-ga-api";
	private static final JsonFactory JSON_FACTORY = GsonFactory
			.getDefaultInstance();
	private static NetHttpTransport httpTransport;
	private static FileDataStoreFactory dataStoreFactory;

	private static String projectName;
	private static String domainUrl;
	private static String countryTimeZone;
	private static String timeZone;
	private static String GTMAccountId;
	private static String GAAccountId;
	private static String[] users;

	public static void main(String[] args) {
		try {

			System.out.print("Enter project name: ");
			projectName = System.console().readLine().trim();

			System.out.print("Enter domain URL [https]: ");
			domainUrl = System.console().readLine().trim();
			if (domainUrl.startsWith("//")) {
				domainUrl = "https:" + domainUrl;
			} else if (!domainUrl.startsWith("http")) {
				domainUrl = "https://" + domainUrl;
			}

			System.out.print("Enter country time zone [US]: ");
			countryTimeZone = System.console().readLine().trim();
			if (countryTimeZone.isEmpty()) {
				countryTimeZone = "US";
			}

			System.out.print("Enter time zone [America/New_York]: ");
			timeZone = System.console().readLine().trim();
			if (timeZone.isEmpty()) {
				timeZone = "America/New_York";
			}

			System.out.print("Enter GTM Account ID: ");
			GTMAccountId = System.console().readLine().trim();
			if (GTMAccountId.isEmpty()) {
				System.out.println("GTM Account ID is mandatory");
				System.exit(1);
			}

			System.out.print("Enter GA Account ID: ");
			GAAccountId = System.console().readLine().trim();
			if (GAAccountId.isEmpty()) {
				System.out.println("GA Account ID is mandatory");
				System.exit(1);
			}
			
			System.out.print("Enter user's email to be added [comma separated list]: ");
			users = System.console().readLine().trim().split(",");

			httpTransport = GoogleNetHttpTransport.newTrustedTransport();
			dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);

			// Authorization flow.
			Credential credential = authorize();
			TagManager manager = new TagManager.Builder(httpTransport,
					JSON_FACTORY, credential).setApplicationName(
					APPLICATION_NAME).build();

			Analytics analytics = new Analytics.Builder(httpTransport,
					JSON_FACTORY, credential).setApplicationName(
					APPLICATION_NAME).build();

			// Get tag manager account ID.
			//GTMAccountId = "56599124"; // for testing

			// Get analytics account ID
			//GAAccountId = "60767497"; // for testing

			// Create new GA Web Property for GAAccountId
			System.out.print("Creating new GA Web Property for account " + GAAccountId + ": " );
			Webproperty wp = createGAWebProperty(analytics, GAAccountId);
			System.out.println("[" + wp.getId() + "]");

			// Create new GTM Container for GTMAccountId
			System.out.print("Creating new GTM Container for account " + GTMAccountId + ": " );
			String containerId = createGTMContainer(manager).getContainerId();
			System.out.println("[" + containerId + "]");

			// Create new Universal Analytics tag for GTM Container containerId
			System.out.print("Creating new Universal Analytics tag for GTM Container " + containerId + ": " );
			Tag ua = createUniversalAnalyticsTag(GTMAccountId, containerId,
					wp.getId(), manager);

			// Create default macros from existing container
			createDefaultMacros(manager, GTMAccountId, containerId);

			// Create the All pages rule.
			Rule rule = createAllPagesRule(GTMAccountId, containerId, manager);

			// Update the UA tag to fire based on the All pages rule.
			fireTagOnRule(ua, rule);
			ua = manager.accounts().containers().tags()
					.update(GTMAccountId, containerId, ua.getTagId(), ua)
					.execute();
			System.out.println("[Ok]");
			
			System.out.print("Adding User permissions to GTM Container and GA Web Property: " );
			addUsersPermission(manager, analytics, wp, containerId);
			System.out.println("[Ok]");
			
			// Create a version and publish automatically
			System.out.print("Creating a new version for the GTM Container " + containerId + ": " );
			createFirstVersionAndPublish(manager, containerId);
			System.out.println("[Published]");

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void addUsersPermission(TagManager manager,
			Analytics analytics, Webproperty wp, String containerId)
			throws IOException {
		for (String user : users) {
			if (user == null || user.length() == 0) {
				continue;
			}
			try {
				user = user.trim();
					
				// GA permission
				// Construct the user reference object.
				UserRef userRef = new UserRef();
				userRef.setEmail(user);

				// Construct the permissions object.
				Permissions permissions = new Permissions();
				List<String> local = Arrays.asList("edit");
				permissions.setLocal(local);

				// Construct the body of the request
				EntityUserLink body = new EntityUserLink();
				body.setPermissions(permissions);
				body.setUserRef(userRef);

				analytics.management().webpropertyUserLinks().insert(GAAccountId, wp.getId(),
						body).execute();
				
				// GTM permission
				// Construct the container access object.
				ContainerAccess container = new ContainerAccess();
				container.setContainerId(containerId);
				container.setPermission(Arrays.asList("read", "edit", "delete", "publish"));

				// Construct the account access object.
				AccountAccess account = new AccountAccess();
				account.setPermission(Arrays.asList("read"));

				// Construct the user access object.
				UserAccess userAccess = new UserAccess();
				userAccess.setEmailAddress(user);
				userAccess.setAccountAccess(account);
				userAccess.setContainerAccess(Arrays.asList(container));
				
				manager.accounts().permissions().create(GTMAccountId, userAccess).execute();
			} catch (Exception e) {
				System.out.println("");
				System.out.print(user + " is not a valid Google Account ");
			}
		}
	}

	private static Container createGTMContainer(TagManager manager)
			throws IOException {
		Container container = new Container();
		container.setName(GTM_CONTAINER_PREFIX + projectName);
		List<String> domains = new ArrayList<String>();
		domains.add(domainUrl);
		container.setDomainName(domains);
		container.setTimeZoneCountryId(countryTimeZone);
		container.setTimeZoneId(timeZone);
		container.setUsageContext(Arrays.asList("web"));
		container = manager.accounts().containers()
				.create(GTMAccountId, container).execute();
		return container;
	}

	private static void createFirstVersionAndPublish(TagManager manager,
			String containerId) throws IOException {
		CreateContainerVersionRequestVersionOptions version = new CreateContainerVersionRequestVersionOptions();
		version.setName("1");
		version.setNotes("Initial Version");
		CreateContainerVersionResponse versionResponse = manager.accounts()
				.containers().versions()
				.create(GTMAccountId, containerId, version).execute();
		manager.accounts()
				.containers()
				.versions()
				.publish(
						GTMAccountId,
						containerId,
						versionResponse.getContainerVersion()
								.getContainerVersionId()).execute();
	}

	private static void createDefaultMacros(TagManager manager,
			String accountId, String containerId) throws IOException {
		ListMacrosResponse macros = manager.accounts().containers().macros()
				.list(GTM_ALCON_ACCOUNT_ID, GTM_ALCON_CONTAINER_ID).execute();
		for (Macro macro : macros.getMacros()) {
			manager.accounts().containers().macros()
					.create(accountId, containerId, macro).execute();
		}
	}

	private static Webproperty createGAWebProperty(Analytics analytics,
			String accountIdGa) throws IOException, Exception {
		Accounts accounts = analytics.management().accounts().list().execute();
		Account account = null;
		for (Account anAccount : accounts.getItems()) {
			if (anAccount.getId().equalsIgnoreCase(accountIdGa)) {
				account = anAccount;
				break;
			}
		}

		if (account == null) {
			throw new Exception("GA Account with id " + accountIdGa
					+ " not found");
		}

		Webproperty body = new Webproperty();
		body.setWebsiteUrl(domainUrl);
		body.setName(GA_CONTAINER_PREFIX + projectName);
		body.setIndustryVertical("HEALTHCARE");

		body = analytics.management().webproperties()
				.insert(account.getId(), body).execute();
		
		// Construct the body of the View request and set its properties.
		Profile profile = new Profile();
		profile.setName("All Web Site Data");
		profile.setTimezone(timeZone);

		analytics.management().profiles().insert(accountIdGa, body.getId(),
		      profile).execute();
		
		return body;
	}

	private static Credential authorize() throws Exception {
		// Load client secrets.
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
				JSON_FACTORY,
				new InputStreamReader(ContainerWebPropertyCreation.class
						.getResourceAsStream(CLIENT_SECRET_JSON_RESOURCE)));

		// Set up authorization code flow for all auth scopes.
		List<String> scopes = new ArrayList<String>();
		scopes.addAll(TagManagerScopes.all());
		scopes.addAll(AnalyticsScopes.all());
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
				httpTransport, JSON_FACTORY, clientSecrets, scopes)
				.setDataStoreFactory(dataStoreFactory).build();

		// Authorize.
		return new AuthorizationCodeInstalledApp(flow,
				new LocalServerReceiver()).authorize("user");
	}

	/**
	 * Create the Universal Analytics Tag.
	 * 
	 * @param accountId
	 *            the ID of the account holding the container.
	 * @param containerId
	 *            the ID of the container to create the tag in.
	 * @param service
	 *            the Tag Manager service object.
	 * @return the newly created Tag resource.
	 * @throws IOException
	 */
	private static Tag createUniversalAnalyticsTag(String accountId,
			String containerId, String webPropertyId, TagManager service)
			throws IOException {
		Tag ua = new Tag();
		ua.setName(projectName + " Page View");
		ua.setType("ua");

		List<Parameter> uaParams = new ArrayList<Parameter>();
		Parameter trackingId = new Parameter();
		trackingId.setKey("trackingId").setValue(webPropertyId)
				.setType("template");
		uaParams.add(trackingId);
		
		Parameter anonymizeIp = new Parameter();
		anonymizeIp.setKey("anonymizeIp").setValue("true")
				.setType("boolean");
		uaParams.add(anonymizeIp);
		
		Parameter anonymizeIpList = new Parameter();
		Parameter arg0 = new Parameter();
		arg0.setKey("fieldName").setValue("anonymizeIp")
				.setType("template");
		Parameter arg1 = new Parameter();
		arg1.setKey("value").setValue("true")
				.setType("template");
		Parameter map = new Parameter();
		map.setType("map").setMap(Arrays.asList(arg0, arg1));
		anonymizeIpList.setKey("fieldsToSetCustomUi").setList(Arrays.asList(map))
				.setType("list");		
		uaParams.add(anonymizeIpList);

		ua.setParameter(uaParams);
		ua = service.accounts().containers().tags()
				.create(accountId, containerId, ua).execute();

		return ua;
	}

	/**
	 * Create the All pages Rule.
	 * 
	 * @param accountId
	 *            the ID of the account holding the container.
	 * @param containerId
	 *            the ID of the container to create the rule in.
	 * @param service
	 *            the Tag Manager service object.
	 * 
	 * @return the newly created Rule resource.
	 * @throws IOException
	 **/
	private static Rule createAllPagesRule(String accountId,
			String containerId, TagManager service) throws IOException {
		Rule rule = new Rule();
		rule.setName("All pages");

		List<Condition> conditions = new ArrayList<Condition>();
		Condition matchRegex = new Condition();
		matchRegex.setType("matchRegex");
		List<Parameter> params = new ArrayList<Parameter>();
		params.add(new Parameter().setKey("arg0").setValue("{{url}}")
				.setType("template"));
		params.add(new Parameter().setKey("arg1").setValue(".*")
				.setType("template"));
		matchRegex.setParameter(params);
		conditions.add(matchRegex);

		rule.setCondition(conditions);
		rule = service.accounts().containers().rules()
				.create(accountId, containerId, rule).execute();
		return rule;
	}

	/**
	 * Update a Tag with a Rule.
	 * 
	 * @param tag
	 *            the tag to associate with the rule.
	 * @param rule
	 *            the rule to associate with the tag.
	 * 
	 */
	private static void fireTagOnRule(Tag tag, Rule rule) {
		List<String> firingRuleIds = new ArrayList<String>();
		firingRuleIds.add(rule.getRuleId());
		tag.setFiringRuleId(firingRuleIds);
	}

}
