package org.apereo.cas.web.view;

import org.apereo.cas.CasViewConstants;
import org.apereo.cas.authentication.ProtocolAttributeEncoder;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.services.web.view.AbstractDelegatingCasView;
import org.springframework.web.servlet.View;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * Renders and prepares CAS2 views. This view is responsible
 * to simply just prep the base model, and delegates to
 * a the real view to render the final output.
 *
 * @author Misagh Moayyed
 * @since 4.1.0
 */
public class Cas20ResponseView extends AbstractDelegatingCasView {

    public Cas20ResponseView(final boolean successResponse, 
                             final ProtocolAttributeEncoder protocolAttributeEncoder, 
                             final ServicesManager servicesManager, 
                             final String authenticationContextAttribute, 
                             final View view) {
        super(successResponse, protocolAttributeEncoder, servicesManager, authenticationContextAttribute, view);
    }

    @Override
    protected void prepareMergedOutputModel(final Map<String, Object> model, final HttpServletRequest request,
                                            final HttpServletResponse response) throws Exception {
        super.putIntoModel(model, CasViewConstants.MODEL_ATTRIBUTE_NAME_PRINCIPAL, getPrincipal(model));
        super.putIntoModel(model, CasViewConstants.MODEL_ATTRIBUTE_NAME_CHAINED_AUTHENTICATIONS, getChainedAuthentications(model));
        super.putIntoModel(model, CasViewConstants.MODEL_ATTRIBUTE_NAME_PRIMARY_AUTHENTICATION, getPrimaryAuthenticationFrom(model));
        logger.debug("Prepared CAS response output model with attribute names {}", model.keySet());
    }
    
}
