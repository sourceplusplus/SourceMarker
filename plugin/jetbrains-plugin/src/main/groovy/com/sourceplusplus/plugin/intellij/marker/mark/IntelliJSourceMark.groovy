package com.sourceplusplus.plugin.intellij.marker.mark

import plus.sourceplus.marker.source.mark.api.SourceMark

/**
 * todo: description
 *
 * @version 0.2.4
 * @since 0.2.4
 * @author <a href="mailto:brandon@srcpl.us">Brandon Fergerson</a>
 */
interface IntelliJSourceMark extends SourceMark {

    void markArtifactSubscribed()

    void markArtifactUnsubscribed()

    void markArtifactHasData()

    boolean isArtifactSubscribed()

    boolean isArtifactDataAvailable()

    boolean isViewable()
}
