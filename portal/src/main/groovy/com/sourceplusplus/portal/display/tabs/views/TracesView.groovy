package com.sourceplusplus.portal.display.tabs.views

import com.sourceplusplus.api.model.trace.ArtifactTraceResult
import com.sourceplusplus.api.model.trace.TraceOrderType
import com.sourceplusplus.portal.display.PortalInterface
import groovy.transform.Canonical
import io.vertx.core.json.JsonArray

import java.util.concurrent.ConcurrentHashMap

/**
 * Holds the current view for the Traces portal tab.
 *
 * @version 0.2.0
 * @since 0.2.0
 * @author <a href="mailto:brandon@srcpl.us">Brandon Fergerson</a>
 */
@Canonical
class TracesView {

    private final Map<TraceOrderType, ArtifactTraceResult> traceResultCache = new ConcurrentHashMap<>()
    private final Map<String, JsonArray> traceStacks = new HashMap<>() //todo: evicting cache
    private final PortalInterface portalInterface
    int innerLevel = 0
    boolean innerTrace
    String rootArtifactQualifiedName
    JsonArray traceStack
    JsonArray innerTraceStack
    TraceOrderType orderType = TraceOrderType.LATEST_TRACES
    ViewType viewType = ViewType.TRACES
    String traceId
    int spanId

    TracesView(PortalInterface portalInterface) {
        this.portalInterface = portalInterface
    }

    void cacheArtifactTraceResult(ArtifactTraceResult artifactTraceResult) {
        traceResultCache.put(artifactTraceResult.orderType(), artifactTraceResult)
    }

    ArtifactTraceResult getArtifactTraceResult() {
        return traceResultCache.get(orderType)
    }

    void cacheTraceStack(String traceId, JsonArray traceStack) {
        traceStacks.put(traceId, traceStack)
    }

    JsonArray getTraceStack(String traceId) {
        return traceStacks.get(traceId)
    }

    void cloneView(TracesView view) {
        traceResultCache.clear()
        traceStacks.clear()
        traceResultCache.putAll(view.traceResultCache)
        traceStacks.putAll(view.traceStacks)
        innerLevel = view.innerLevel
        innerTrace = view.innerTrace
        rootArtifactQualifiedName = view.rootArtifactQualifiedName
        if (view.traceStack) {
            traceStack = new JsonArray().addAll(view.traceStack)
        } else {
            traceStack = null
        }
        if (view.innerTraceStack) {
            innerTraceStack = new JsonArray().addAll(view.innerTraceStack)
        } else {
            innerTraceStack = null
        }
        orderType = view.orderType
        viewType = view.viewType
        traceId = view.traceId
        spanId = view.spanId
    }

    static enum ViewType {
        TRACES, TRACE_STACK, SPAN_INFO
    }
}