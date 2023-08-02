package org.opensearch.migrations.replay.datahandlers.http;

import org.opensearch.migrations.replay.SigV4Signer;

public interface IHttpMessageMetadata {
    String method();

    String uri();

    String protocol();

    ListKeyAdaptingCaseInsensitiveHeadersMap headers();
}
