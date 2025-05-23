/*
 *  Copyright (C) 2010 Ryszard Wiśniewski <brut.alll@gmail.com>
 *  Copyright (C) 2010 Connor Tumbleson <connor.tumbleson@gmail.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package brut.androlib.res.xml;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

public final class ResXmlEncoders {

    private ResXmlEncoders() {
        // Private constructor for utility class
    }

    public static String escapeXmlChars(String str) {
        return StringUtils.replaceEach(
            str,
            new String[]{ "&", "<", "]]>" },
            new String[]{ "&amp;", "&lt;", "]]&gt;" }
        );
    }

    public static String encodeAsResXmlAttr(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }

        char[] chars = str.toCharArray();
        StringBuilder out = new StringBuilder(str.length() + 10);

        switch (chars[0]) {
            case '#':
            case '@':
            case '?':
                out.append('\\');
        }

        for (char c : chars) {
            switch (c) {
                case '\\':
                    out.append('\\');
                    break;
                case '"':
                    out.append("&quot;");
                    continue;
                case '\n':
                    out.append("\\n");
                    continue;
                default:
                    if (isPrintableChar(c)) {
                        break;
                    }
                    out.append(String.format("\\u%04x", (int) c));
                    continue;
            }
            out.append(c);
        }

        return out.toString();
    }

    public static String encodeAsXmlValue(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }

        char[] chars = str.toCharArray();
        StringBuilder out = new StringBuilder(str.length() + 10);

        switch (chars[0]) {
            case '#':
            case '@':
            case '?':
                out.append('\\');
        }

        boolean isInStyleTag = false;
        int startPos = 0;
        boolean enclose = false;
        boolean wasSpace = true;
        for (char c : chars) {
            if (isInStyleTag) {
                if (c == '>') {
                    isInStyleTag = false;
                    startPos = out.length() + 1;
                    enclose = false;
                }
            } else if (c == ' ') {
                if (wasSpace) {
                    enclose = true;
                }
                wasSpace = true;
            } else {
                wasSpace = false;
                switch (c) {
                    case '\\':
                    case '"':
                        out.append('\\');
                        break;
                    case '\'':
                    case '\n':
                        enclose = true;
                        break;
                    case '<':
                        isInStyleTag = true;
                        if (enclose) {
                            out.insert(startPos, '"').append('"');
                        }
                        break;
                    default:
                        if (isPrintableChar(c)) {
                            break;
                        }
                        // let's not write trailing \u0000 if we are at end of string
                        if ((out.length() + 1) == str.length() && c == '\u0000') {
                            continue;
                        }
                        out.append(String.format("\\u%04x", (int) c));
                        continue;
                }
            }
            out.append(c);
        }

        if (enclose || wasSpace) {
            out.insert(startPos, '"').append('"');
        }
        return out.toString();
    }

    public static boolean hasMultipleNonPositionalSubstitutions(String str) {
        Pair<List<Integer>, List<Integer>> subs = findSubstitutions(str, 4);
        List<Integer> nonPositional = subs.getLeft();
        List<Integer> positional = subs.getRight();
        return !nonPositional.isEmpty() && nonPositional.size() + positional.size() > 1;
    }

    public static String enumerateNonPositionalSubstitutionsIfRequired(String str) {
        Pair<List<Integer>, List<Integer>> subs = findSubstitutions(str, 4);
        List<Integer> nonPositional = subs.getLeft();
        List<Integer> positional = subs.getRight();
        if (nonPositional.isEmpty() || nonPositional.size() + positional.size() < 2) {
            return str;
        }

        StringBuilder out = new StringBuilder();
        int pos = 0;
        int count = 0;
        for (int pos2 : nonPositional) {
            out.append(str, pos, ++pos2).append(++count).append('$');
            pos = pos2;
        }
        out.append(str.substring(pos));

        return out.toString();
    }

    /**
     * It returns a pair of:
     *   - a list of offsets of non-positional substitutions. non-pos is defined as any "%" which isn't "%%" nor "%\d+\$"
     *   - a list of offsets of positional substitutions
     */
    private static Pair<List<Integer>, List<Integer>> findSubstitutions(String str, int nonPosMax) {
        if (nonPosMax == -1) {
            nonPosMax = Integer.MAX_VALUE;
        }
        int pos;
        int pos2 = 0;
        List<Integer> nonPositional = new ArrayList<>();
        List<Integer> positional = new ArrayList<>();

        if (str == null) {
            return Pair.of(nonPositional, positional);
        }

        int length = str.length();

        while ((pos = str.indexOf('%', pos2)) != -1) {
            pos2 = pos + 1;
            if (pos2 == length) {
                nonPositional.add(pos);
                break;
            }
            char c = str.charAt(pos2++);
            if (c == '%') {
                continue;
            }
            if (c >= '0' && c <= '9' && pos2 < length) {
                while ((c = str.charAt(pos2++)) >= '0' && c <= '9' && pos2 < length);
                if (c == '$') {
                    positional.add(pos);
                    continue;
                }
            }

            nonPositional.add(pos);
            if (nonPositional.size() >= nonPosMax) {
                break;
            }
        }

        return Pair.of(nonPositional, positional);
    }

    private static boolean isPrintableChar(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return !Character.isISOControl(c) && c != KeyEvent.CHAR_UNDEFINED
                && block != null && block != Character.UnicodeBlock.SPECIALS;
    }
}
