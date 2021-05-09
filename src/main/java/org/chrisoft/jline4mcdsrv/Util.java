package org.chrisoft.jline4mcdsrv;

import com.mojang.brigadier.StringReader;
import org.checkerframework.checker.nullness.qual.NonNull;

final class Util {
    private Util() {
    }

    static @NonNull StringReader prepareStringReader(final @NonNull String buffer) {
        final StringReader stringReader = new StringReader(buffer);
        if (stringReader.canRead() && stringReader.peek() == '/') {
            stringReader.skip();
        }
        return stringReader;
    }
}
