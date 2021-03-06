package org.apereo.cas.web.flow;

import org.apache.commons.lang3.StringUtils;
import org.apereo.cas.CentralAuthenticationService;
import org.apereo.cas.authentication.Authentication;
import org.apereo.cas.authentication.AuthenticationException;
import org.apereo.cas.authentication.AuthenticationSystemSupport;
import org.apereo.cas.services.MultifactorAuthenticationProvider;
import org.apereo.cas.services.MultifactorAuthenticationProviderSelector;
import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.support.oauth.OAuthConstants;
import org.apereo.cas.ticket.registry.TicketRegistrySupport;
import org.apereo.cas.validation.AuthenticationRequestServiceSelectionStrategy;
import org.apereo.cas.web.flow.authentication.BaseMultifactorAuthenticationProviderEventResolver;
import org.apereo.cas.web.support.WebUtils;
import org.jasig.cas.client.util.URIBuilder;
import org.springframework.web.util.CookieGenerator;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * This is {@link OidcAuthenticationContextWebflowEventEventResolver}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
public class OidcAuthenticationContextWebflowEventEventResolver extends BaseMultifactorAuthenticationProviderEventResolver {

    public OidcAuthenticationContextWebflowEventEventResolver(final AuthenticationSystemSupport authenticationSystemSupport,
                                                              final CentralAuthenticationService centralAuthenticationService,
                                                              final ServicesManager servicesManager, final TicketRegistrySupport ticketRegistrySupport,
                                                              final CookieGenerator warnCookieGenerator,
                                                              final List<AuthenticationRequestServiceSelectionStrategy> authenticationSelectionStrategies,
                                                              final MultifactorAuthenticationProviderSelector selector) {
        super(authenticationSystemSupport, centralAuthenticationService, servicesManager, ticketRegistrySupport, warnCookieGenerator,
                authenticationSelectionStrategies, selector);
    }

    @Override
    public Set<Event> resolveInternal(final RequestContext context) {
        final RegisteredService service = resolveRegisteredServiceInRequestContext(context);
        final Authentication authentication = WebUtils.getAuthentication(context);
        final HttpServletRequest request = WebUtils.getHttpServletRequest(context);

        if (service == null || authentication == null) {
            logger.debug("No service or authentication is available to determine event for principal");
            return null;
        }

        String acr = request.getParameter(OAuthConstants.ACR_VALUES);
        if (StringUtils.isBlank(acr)) {
            final URIBuilder builderContext = new URIBuilder(context.getFlowExecutionUrl());
            final Optional<URIBuilder.BasicNameValuePair> parameter = builderContext.getQueryParams()
                    .stream().filter(p -> p.getName().equals(OAuthConstants.ACR_VALUES))
                    .findFirst();
            if (parameter.isPresent()) {
                acr = parameter.get().getValue();
            }
        }
        if (StringUtils.isBlank(acr)) {
            logger.debug("No ACR provided in the authentication request");
            return null;
        }
        final Set<String> values = org.springframework.util.StringUtils.commaDelimitedListToSet(acr);
        if (values.isEmpty()) {
            logger.debug("No ACR provided in the authentication request");
            return null;
        }

        final Map<String, MultifactorAuthenticationProvider> providerMap =
                WebUtils.getAvailableMultifactorAuthenticationProviders(this.applicationContext);
        if (providerMap == null || providerMap.isEmpty()) {
            logger.error("No multifactor authentication providers are available in the application context to handle {}", values);
            throw new AuthenticationException();
        }

        final Collection<MultifactorAuthenticationProvider> flattenedProviders = flattenProviders(providerMap.values());
        final Optional<MultifactorAuthenticationProvider> provider = flattenedProviders
                .stream()
                .filter(v -> values.contains(v.getId())).findAny();

        if (provider.isPresent()) {
            return Collections.singleton(new Event(this, provider.get().getId()));
        }
        logger.warn("The requested authentication class {} cannot be satisfied by any of the MFA providers available", values);
        throw new AuthenticationException();
    }
}
