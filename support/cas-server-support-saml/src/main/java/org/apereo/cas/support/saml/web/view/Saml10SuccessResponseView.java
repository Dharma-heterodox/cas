package org.apereo.cas.support.saml.web.view;

import org.apereo.cas.authentication.Authentication;
import org.apereo.cas.authentication.ProtocolAttributeEncoder;
import org.apereo.cas.authentication.principal.Principal;
import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.support.saml.util.Saml10ObjectBuilder;
import org.apereo.cas.util.CollectionUtils;
import org.apereo.cas.util.DateTimeUtils;
import org.apereo.cas.CasProtocolConstants;
import org.apereo.cas.authentication.RememberMeCredential;
import org.apereo.cas.authentication.principal.Service;
import org.apereo.cas.support.saml.authentication.SamlAuthenticationMetaDataPopulator;
import org.apereo.cas.web.support.ArgumentExtractor;
import org.opensaml.saml.saml1.core.Assertion;
import org.opensaml.saml.saml1.core.AuthenticationStatement;
import org.opensaml.saml.saml1.core.Conditions;
import org.opensaml.saml.saml1.core.Response;
import org.opensaml.saml.saml1.core.StatusCode;
import org.opensaml.saml.saml1.core.Subject;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of a view to return a SAML SOAP response and assertion, based on
 * the SAML 1.1 specification.
 * <p>
 * If an AttributePrincipal is supplied, then the assertion will include the
 * attributes from it (assuming a String key/Object value pair). The only
 * Authentication attribute it will look at is the authMethod (if supplied).
 * <p>
 * Note that this class will currently not handle proxy authentication.
 * <p>
 *
 * @author Scott Battaglia
 * @author Marvin S. Addison
 * @since 3.1
 */
public class Saml10SuccessResponseView extends AbstractSaml10ResponseView {

    private final String issuer;

    private final String rememberMeAttributeName;

    private final String defaultAttributeNamespace;

    public Saml10SuccessResponseView(final ProtocolAttributeEncoder protocolAttributeEncoder,
                                     final ServicesManager servicesManager,
                                     final String authenticationContextAttribute,
                                     final Saml10ObjectBuilder samlObjectBuilder,
                                     final ArgumentExtractor samlArgumentExtractor,
                                     final String encoding,
                                     final int skewAllowance,
                                     final String issuer,
                                     final String defaultAttributeNamespace) {
        super(true, protocolAttributeEncoder, servicesManager, authenticationContextAttribute,
                samlObjectBuilder, samlArgumentExtractor, encoding, skewAllowance);
        this.issuer = issuer;
        this.rememberMeAttributeName = CasProtocolConstants.VALIDATION_REMEMBER_ME_ATTRIBUTE_NAME;
        this.defaultAttributeNamespace = defaultAttributeNamespace;
    }

    @Override
    protected void prepareResponse(final Response response, final Map<String, Object> model) {

        final ZonedDateTime issuedAt = DateTimeUtils.zonedDateTimeOf(response.getIssueInstant());
        final Service service = getAssertionFrom(model).getService();
        logger.debug("Preparing SAML response for service {}", service);
        
        final Authentication authentication = getPrimaryAuthenticationFrom(model);
        final Collection<Object> authnMethods = CollectionUtils.toCollection(authentication.getAttributes()
                .get(SamlAuthenticationMetaDataPopulator.ATTRIBUTE_AUTHENTICATION_METHOD));
        logger.debug("Authentication methods found are [{}]", authnMethods);

        final Principal principal = getPrincipal(model);
        final AuthenticationStatement authnStatement = this.samlObjectBuilder.newAuthenticationStatement(
                authentication.getAuthenticationDate(), authnMethods, principal.getId());
        logger.debug("Built authentication statement for [{}] dated at {}", principal, authentication.getAuthenticationDate());
        
        final Assertion assertion = this.samlObjectBuilder.newAssertion(authnStatement, this.issuer, issuedAt,
                this.samlObjectBuilder.generateSecureRandomId());
        logger.debug("Built assertion for issuer {} dated at ", this.issuer, issuedAt);
        
        final Conditions conditions = this.samlObjectBuilder.newConditions(issuedAt, service.getId(), this.skewAllowance);
        assertion.setConditions(conditions);
        logger.debug("Built assertion conditions for issuer {} and service {} ", this.issuer, service.getId());
        
        final Subject subject = this.samlObjectBuilder.newSubject(principal.getId());
        logger.debug("Built subject for principal {}", principal);

        final Map<String, Object> attributesToSend = prepareSamlAttributes(model, service);
        logger.debug("Authentication statement shall include these attributes {}", attributesToSend);
        
        if (!attributesToSend.isEmpty()) {
            assertion.getAttributeStatements().add(this.samlObjectBuilder.newAttributeStatement(
                    subject, attributesToSend, this.defaultAttributeNamespace));
        }

        response.setStatus(this.samlObjectBuilder.newStatus(StatusCode.SUCCESS, null));
        logger.debug("Set status code SUCCESS to response");
        
        response.getAssertions().add(assertion);
    }


    /**
     * Prepare saml attributes. Combines both principal and authentication
     * attributes. If the authentication is to be remembered, uses {@link #rememberMeAttributeName}
     * for the remember-me attribute name.
     *
     * @param model the model
     * @return the final map
     * @since 4.1.0
     */
    private Map<String, Object> prepareSamlAttributes(final Map<String, Object> model, final Service service) {
        final Map<String, Object> authnAttributes = new HashMap<>(getAuthenticationAttributesAsMultiValuedAttributes(model));        
        if (isRememberMeAuthentication(model)) {
            authnAttributes.remove(RememberMeCredential.AUTHENTICATION_ATTRIBUTE_REMEMBER_ME);
            authnAttributes.put(this.rememberMeAttributeName, Boolean.TRUE.toString());
        }
        logger.debug("Retrieved authentication attributes {} from the model", authnAttributes);
        
        final RegisteredService registeredService = this.servicesManager.findServiceBy(service);
        final Map<String, Object> attributesToReturn = new HashMap<>();
        attributesToReturn.putAll(getPrincipalAttributesAsMultiValuedAttributes(model));
        attributesToReturn.putAll(authnAttributes);

        decideIfCredentialPasswordShouldBeReleasedAsAttribute(attributesToReturn, model, registeredService);
        decideIfProxyGrantingTicketShouldBeReleasedAsAttribute(attributesToReturn, model, registeredService);

        logger.debug("Beginning to encode attributes [{}] for service [{}]", attributesToReturn, registeredService.getServiceId());
        final Map<String, Object> finalAttributes = this.protocolAttributeEncoder.encodeAttributes(attributesToReturn, registeredService);
        logger.debug("Final collection of attributes are [{}]", finalAttributes);
        
        return finalAttributes;
    }

}
