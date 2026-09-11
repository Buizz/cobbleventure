package dev.buizz.cobbleventure.playermenu;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/** Normalizes a built-in greedy string so command-tree sync needs no custom serializer. */
final class ProgressIdArgument {
    private ProgressIdArgument() {}

    static String parse(String input) throws CommandSyntaxException {
        StringReader reader = new StringReader(input);
        String id;
        if (reader.canRead() && StringReader.isQuotedStringStart(reader.peek())) {
            id = reader.readString();
        } else {
            int start = reader.getCursor();
            while (reader.canRead() && !Character.isWhitespace(reader.peek())) reader.skip();
            id = reader.getString().substring(start, reader.getCursor());
        }
        if (id.isEmpty() || reader.canRead()) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(reader);
        }
        return id;
    }
}
