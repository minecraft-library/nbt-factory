package lib.minecraft.nbt.tag;

import dev.simplified.annotations.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Writer of the canonical text form {@link Tag#toCanonicalString()} returns, which is the form
 * vanilla's own tag rendering writes.
 *
 * <ul>
 *   <li><b>Numbers</b> - the value with its type's suffix: {@code 1b}, {@code 1s}, {@code 1},
 *       {@code 1L}, {@code 1.0f}, {@code 1.0d}.</li>
 *   <li><b>Strings</b> - quoted, in double quotes unless a double quote comes before any single
 *       one, with the backslash, the chosen quote and the control characters escaped.</li>
 *   <li><b>Arrays</b> - {@code [B;1B,2B]}, {@code [I;1,2]}, {@code [L;1L,2L]}.</li>
 *   <li><b>Lists</b> - {@code [a,b]}, each element as the list holds it.</li>
 *   <li><b>Compounds</b> - {@code {a:1,b:2}}, the keys sorted, each bare where it is a plain
 *       identifier other than {@code true} or {@code false} and quoted otherwise.</li>
 * </ul>
 */
@UtilityClass
class CanonicalTagWriter {

    /**
     * Keys written without quotes.
     */
    private static final @NotNull Pattern UNQUOTED_KEY = Pattern.compile("[A-Za-z._]+[A-Za-z0-9._+-]*");

    /**
     * Hex digits of a control character written as a {@code \x} escape.
     */
    private static final @NotNull HexFormat HEX = HexFormat.of().withUpperCase();

    /**
     * Appends the canonical text form of a tag.
     *
     * @param tag the tag
     * @param builder the destination
     * @throws UnsupportedOperationException if the tag's id is no NBT type
     */
    static void write(@NotNull Tag<?> tag, @NotNull StringBuilder builder) {
        switch (tag.getId()) {
            case 0 -> builder.append("END");
            case 1 -> builder.append(((NumericalTag<?>) tag).byteValue()).append('b');
            case 2 -> builder.append(((NumericalTag<?>) tag).shortValue()).append('s');
            case 3 -> builder.append(((NumericalTag<?>) tag).intValue());
            case 4 -> builder.append(((NumericalTag<?>) tag).longValue()).append('L');
            case 5 -> builder.append(((NumericalTag<?>) tag).floatValue()).append('f');
            case 6 -> builder.append(((NumericalTag<?>) tag).doubleValue()).append('d');
            case 7 -> {
                byte[] values = ((ByteArrayTag) tag).getValue();
                builder.append("[B;");

                for (int i = 0; i < values.length; i++) {
                    if (i > 0) builder.append(',');
                    builder.append(values[i]).append('B');
                }

                builder.append(']');
            }
            case 8 -> quote(((StringTag) tag).getValue(), builder);
            case 9 -> {
                ListTag<?> list = (ListTag<?>) tag;
                builder.append('[');
                int index = 0;

                for (Tag<?> element : list) {
                    if (index++ > 0) builder.append(',');
                    write(element, builder);
                }

                builder.append(']');
            }
            case 10 -> {
                List<Map.Entry<String, Tag<?>>> entries = new ArrayList<>(((CompoundTag) tag).entrySet());
                entries.sort(Map.Entry.comparingByKey());
                builder.append('{');

                for (int i = 0; i < entries.size(); i++) {
                    if (i > 0) builder.append(',');
                    Map.Entry<String, Tag<?>> entry = entries.get(i);
                    key(entry.getKey(), builder);
                    builder.append(':');
                    write(entry.getValue(), builder);
                }

                builder.append('}');
            }
            case 11 -> {
                int[] values = ((IntArrayTag) tag).getValue();
                builder.append("[I;");

                for (int i = 0; i < values.length; i++) {
                    if (i > 0) builder.append(',');
                    builder.append(values[i]);
                }

                builder.append(']');
            }
            case 12 -> {
                long[] values = ((LongArrayTag) tag).getValue();
                builder.append("[L;");

                for (int i = 0; i < values.length; i++) {
                    if (i > 0) builder.append(',');
                    builder.append(values[i]).append('L');
                }

                builder.append(']');
            }
            default -> throw new UnsupportedOperationException("Tag with id " + tag.getId() + " is not supported.");
        }
    }

    /**
     * Appends a compound key, bare where it is a plain identifier and quoted otherwise.
     *
     * @param key the key
     * @param builder the destination
     */
    private static void key(@NotNull String key, @NotNull StringBuilder builder) {
        if (!key.equalsIgnoreCase("true") && !key.equalsIgnoreCase("false") && UNQUOTED_KEY.matcher(key).matches())
            builder.append(key);
        else
            quote(key, builder);
    }

    /**
     * Appends a string quoted and escaped - quoted with the one of {@code "} and {@code '} it does
     * not meet first, so only that quote needs escaping, and with {@code "} when it holds neither.
     *
     * @param value the string
     * @param builder the destination
     */
    private static void quote(@NotNull String value, @NotNull StringBuilder builder) {
        int start = builder.length();
        builder.append(' ');
        char quote = 0;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            if (c == '\\')
                builder.append("\\\\");
            else if (c == '"' || c == '\'') {
                if (quote == 0)
                    quote = c == '"' ? '\'' : '"';

                if (quote == c)
                    builder.append('\\');

                builder.append(c);
            } else {
                String escape = controlEscape(c);

                if (escape != null)
                    builder.append('\\').append(escape);
                else
                    builder.append(c);
            }
        }

        if (quote == 0)
            quote = '"';

        builder.setCharAt(start, quote);
        builder.append(quote);
    }

    /**
     * Returns the escape a control character is written with, after the backslash.
     *
     * @param c the character
     * @return the escape, or {@code null} for a character written as it is
     */
    private static @Nullable String controlEscape(char c) {
        return switch (c) {
            case '\b' -> "b";
            case '\t' -> "t";
            case '\n' -> "n";
            case '\f' -> "f";
            case '\r' -> "r";
            default -> c < ' ' ? "x" + HEX.toHexDigits((byte) c) : null;
        };
    }

}
