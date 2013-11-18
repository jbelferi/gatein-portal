/*
 * Copyright (C) 2012 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.gatein.portal.web.page;

import java.io.IOException;
import java.util.Map;

import javax.xml.namespace.QName;

import juzu.Response;
import juzu.impl.common.PercentCodec;
import juzu.io.Encoding;
import juzu.request.Phase;
import org.gatein.pc.api.cache.CacheLevel;
import org.gatein.portal.web.content.RenderTask;
import org.gatein.portal.web.content.WindowContent;
import org.gatein.portal.web.content.WindowContentContext;
import org.gatein.portal.web.content.portlet.PortletContent;

/**
 * @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a>
 */
public class WindowContext implements WindowContentContext {

    /**
     * Custom encoding.
     */
    public static final Encoding ENCODING = new Encoding("custom") {
        @Override
        public void encodeSegment(CharSequence s, Appendable appendable) throws IOException {
            PercentCodec.RFC3986_SEGMENT.encode(s, appendable);
        }

        @Override
        public void encodeQueryParamName(CharSequence s, Appendable appendable) throws IOException {
            PercentCodec.RFC3986_QUERY_PARAM_NAME.encode(s, appendable);
        }

        @Override
        public void encodeQueryParamValue(CharSequence s, Appendable appendable) throws IOException {
            appendable.append(s);
        }
    };

    /** The related page. */
    public final PageContext page;

    /** . */
    public final String name;

    /** The intrisic state. */
    public final WindowContent state;

    WindowContext(String name, WindowContent state, PageContext page) {
        if (name == null) {
            throw new NullPointerException("No null name accepted");
        }
        if (state == null) {
            throw new NullPointerException("No null state accepted");
        }
        if (page == null) {
            throw new NullPointerException("No null page accepted");
        }

        //
        this.name = name;
        this.page = page;
        this.state = state;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Map<String, String[]> getPublicRenderParameters() {
        return computePublicParameters();
    }

    public Iterable<Map.Entry<QName, String[]>> getPublicParametersChanges(Map<String, String[]> changes) {
        return state.getPublicParametersChanges(changes);
    }

    public Response processAction(
            String windowState,
            String mode,
            Map<String, String[]> interactionState) {
        return state.processAction(this, windowState, mode, interactionState);
    }

    /**
     * Capture some context here.
     *
     * @return the callable for rendering a portlet
     */
    public RenderTask createRenderTask() {
        return state.createRender(this);
    }

    public Response serveResource(String id, Map<String, String[]> resourceState) {
        return state.serveResource(this, id, resourceState);
    }

    public Map<String, String[]> computePublicParameters() {
        Map<String, String[]> publicParameters;
        if (page.hasParameters()) {
            publicParameters = state.computePublicParameters(page.getParameters());
        } else {
            publicParameters = null;
        }
        return publicParameters;
    }

    /**
     * Encode the state of this window in the dispatch object.
     *
     * @param dispatch the dispatch
     */
    public void encode(Phase.View.Dispatch dispatch) {
        encode(dispatch, state);
    }

    /**
     * Encode the provided state in the dispatch object for the current window
     *
     * @param dispatch the dispatch
     * @param state the state to encode
     */
    public void encode(Phase.View.Dispatch dispatch, WindowContent state) {
        encode(dispatch, state.getParameters(), state.getWindowState(), state.getMode());
    }

    /**
     * Encode the provided state in the dispatch object for the current window
     *
     * @param dispatch the dispatch
     * @param parameters the parameters
     * @param windowState the window state
     * @param mode the mode
     */
    public void encode(Phase.View.Dispatch dispatch, String parameters, String windowState, String mode) {
        if (parameters != null) {
            dispatch.setParameter(WindowContext.ENCODING, "javax.portlet.p." + name, parameters);
        }
        if (windowState != null) {
            dispatch.setParameter("javax.portlet.w." + name, windowState);
        }
        if (mode != null) {
            dispatch.setParameter("javax.portlet.m." + name, mode);
        }
    }

    @Override
    public String toString() {
        return "WindowState[name=" + name + ",parameters=" + state.getParameters() + "]";
    }

    public String createRenderURL(
            WindowContent content,
            Map<String, String[]> changes) {
        PageContext.Builder a = page.builder();
        if (content != null) {
            a.setWindow(name, content);
        }
        if (changes.size() > 0) {
            a.apply(getPublicParametersChanges(changes));
        }
        return a.build().getDispatch().toString();
    }

    public String createActionURL(
            Map<String, String[]> parameters,
            String targetWindowState, String targetMode) {

        Phase.View.Dispatch dispatch = Controller_.index(page.state.path, "action", name, targetWindowState, targetMode);

        // Encode all windows
        for (WindowContext w : page.windows) {
            w.encode(dispatch);
        }

        // Encode page parameters only if we have some
        if (page.getParameters() != null && page.getParameters().size() > 0) {
            page.encodeParameters(dispatch);
        }

        //
        if (parameters != null) {
            for (Map.Entry<String, String[]> parameter : parameters.entrySet()) {
                dispatch.setParameter(parameter.getKey(), parameter.getValue());
            }
        }

        //
        return dispatch.toString();
    }

    public String creatResourceURL(CacheLevel cacheLevel, Map<String, String[]> parameters, String id) {

        Phase.View.Dispatch dispatch = Controller_.index(page.state.path, "resource", name, state.getWindowState(), state.getMode());

        //
        if (cacheLevel == CacheLevel.PORTLET || cacheLevel == CacheLevel.PAGE) {

            // Encode this window
            String ww = state.getParameters();
            if (ww == null) {
                ww = new Encoder(PortletContent.NO_PARAMETERS).encode();
            }
            encode(dispatch, ww, state.getWindowState(), state.getMode());

            //
            if (cacheLevel == CacheLevel.PAGE) {

                // Encode all windows
                for (WindowContext w : page.windows) {
                    if (w != this) {
                        w.encode(dispatch);
                    }
                }

                // Encode page parameters (even if it's empty)
                page.encodeParameters(dispatch);
            }
        }

        // Append provided parameters
        if (parameters != null) {
            for (Map.Entry<String, String[]> parameter : parameters.entrySet()) {
                dispatch.setParameter(parameter.getKey(), parameter.getValue());
            }
        }

        //
        if (id != null) {
            dispatch.setParameter("javax.portlet.r", id);
        }

        //
        return dispatch.toString();
    }
}
