package com.bigclue.archive;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to clean up HTML content, especially from email messages or blog posts.
 * Uses String manipulation rather than a full HTML parser.
 */
@SuppressWarnings({"java:S106", "java:S112", "java:S110", "java:S1148", "java:S1192"})
public class HtmlCleaner {
  private HtmlCleaner() {
    // Prevent instantiation
  }

  private static final Pattern BODY_PATTERN = Pattern.compile("(?is)(.*?<body[^>]*>)(.*?)(</body>.*)", Pattern.DOTALL);
  static String cleanHtml(String html) {
    // get everything up to <body>; then body; then everything after </body>
    Matcher m = BODY_PATTERN.matcher(html);
    if (m.matches()) {
      String beforeBody = m.group(1);
      String body = m.group(2);
      String afterBody = m.group(3);
      body = cleanBody(body);
      html = beforeBody + body + afterBody;
    } else {
      throw new IllegalArgumentException("Invalid html string: " + html);
    }
    return html;
  }

  static String cleanBody(String body) {
    // Get rid of <span> and <font> tags
    body = body.replaceAll("(?is)</?(span|font|blockquote)[^>]*>", "");
    // Make sure images are surrounded by <p> tags. Duplicates will be removed later.
    body = body.replaceAll("(?i)\\s*(<img[^>]*>)", "<p>$1</p>");
    // Replace <br> tags that are between text with a special placeholder
    body = replaceActualLineBreaks(body);
    // Replace opening and closing br, p, and div tags with a placeholder
    body = body.replaceAll("(?is)</?(br|p|div)[^>]*>", "<¶>");
    // Remove multiple consecutive placeholders
    int len;
    do {
      len = body.length();
      body = body.replaceAll("\\s*<¶>\\s*<¶>\\s*", "<¶>");
    } while (body.length() < len);
    // Split on placeholders
    String[] parts = body.split("<¶>");
    List<String> paragraphs = new ArrayList<>();
    for (String part : parts) {
      part = part.trim();
      if (!part.isEmpty()) {
        paragraphs.add("<p>" + part + "</p>");
      }
    }
    body = String.join("\n", paragraphs) + "\n";
    body = body.replace("<ß>", "<br>\n");
    body = body.replaceAll("(?is)(</?(p|br)>)\\s*&nbsp;\\s*", "$1");
    body = body.replaceAll("(?is)\\s*&nbsp;\\s*(</?(p|br)>)", "$1");
    // Fix <p><a href="..."></p><p><img src="..."></p><p></a></p> => <p><a href="..."><img src="..."></a></p>
    body = removeInnerParagraphs(body, "a", false);
    body = removeInnerParagraphs(body, "table", true);
    body = body.replaceAll("<p>\\s*(<a [^>]*>)\\s*</p>\\s*<p>\\s*(<img[^>]*>)\\s*</p>\\s*<p>\\s*</a>\\s*</p>",
        "<p>$1$2</a></p>");
    body = body.replaceAll("<table[^>]*class=\"tr-caption-container\"[^>]*>",
        "<table class=\"tr-caption-container\">");
    return body;
  }

  /*
  Remove paragraph tags that are inside the specified tag, and ensure that there are <p> around the entire tag.
   */
  private static String removeInnerParagraphs(String body, String tag, boolean shouldSurroundWithP) {
    Pattern tagPattern = Pattern.compile("(?is)(<\\s*" + tag + "[^>]*>.*?<\\s*/\\s*" + tag + "\\s*>)");
    Matcher matcher = tagPattern.matcher(body);
    StringBuilder result = new StringBuilder();
    int lastEnd = 0;

    while (matcher.find()) {
      int start = matcher.start();
      int end = matcher.end();

      // Add everything before this match
      String beforeMatch = body.substring(lastEnd, start);
      result.append(beforeMatch);

      // Get the matched tag content and remove inner <p> and </p> tags
      String matchedContent = matcher.group(1);
      String cleanedContent = matchedContent.replaceAll("(?is)\\s*</?p[^>]*>\\s*", "");

      if (shouldSurroundWithP) {
        // Check if we need to add <p> before
        String beforeTrimmed = beforeMatch.trim();
        if (!beforeTrimmed.endsWith("<p>") && !beforeTrimmed.matches("(?i).*<p[^>]*>\\s*$")) {
          result.append("<p>");
        }
      }

      // Add the cleaned content
      result.append(cleanedContent);

      if (shouldSurroundWithP) {
        // Check if we need to add </p> after
        String afterTrimmed = body.substring(end).trim();
        String remainingString = body.substring(end);
        String remainingTrimmed = remainingString.trim();
        if (!afterTrimmed.matches("(?i)^\\s*</p[^>]*>.*") && !remainingTrimmed.matches("(?i)^\\s*</\\s*p\\s*>.*")) {
          result.append("</p>\n");
        }
        // Check if there's a </p> without a preceding <p> in the remaining string
        Pattern closingPPattern = Pattern.compile("(?i)</\\s*p\\s*>");
        Matcher closingPMatcher = closingPPattern.matcher(remainingTrimmed);
        int closingPIndex = closingPMatcher.find() ? closingPMatcher.start() : remainingTrimmed.length();
        if (closingPIndex == -1) {
          closingPIndex = remainingTrimmed.length();
        }
        String beforeClosingP = remainingTrimmed.substring(0, closingPIndex);
        if (!beforeClosingP.matches("(?i).*<\\s*p[^>]*>.*")) {
          // Insert <p> at the beginning of remaining string
          result.append("<p>");
        }
      }
      lastEnd = end;
    }

    // Add any remaining content after the last match
    result.append(body.substring(lastEnd).trim());

    return result.toString().trim() + "\n";
  }

  // Replace <br> tags that are between text with a special placeholder, <ß>
  // Leave other <br> tags, which will be converted to paragraph breaks later.
  // This handles cases where simple line breaks were used in an e-mail message,
  //   such as in some early fixed-width messages, poems, or addresses.
  static String replaceActualLineBreaks(String body) {
    Pattern p = Pattern.compile("(?is)([^>\\s])\\s*<br[^>]*>\\s*([^<\\s])");
    Matcher m = p.matcher(body);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      m.appendReplacement(sb, m.group(1) + "<ß>" + m.group(2));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  static String cleanHtml1(String origHtml) {
    System.out.println("### Original HTML:");
    System.out.println(origHtml);

    // Simplify <br ...> to <br>
    origHtml = origHtml.replaceAll("(?is)<br[^>]*>", "<br>");
    // Replace <div><br></div> with <br>
    origHtml = origHtml.replaceAll("(?is)<div>\\s*<br\\s*/?>\\s*</div>", "<br>");
    origHtml = origHtml.replaceAll("(?is)<div>\\s*</div>", "");
    origHtml = origHtml.replaceAll("(?is)<div>\\s*<br>", "<div>");
    origHtml = origHtml.replaceAll("(?is)<br>\\s*</div>", "</div>");
    // Replace <div>...</div> with <p>...</p>
    origHtml = removeNonNestedDivs(origHtml);
    origHtml = origHtml.replaceAll("(?is)<p></p>", "");
    origHtml = origHtml.replaceAll("(?is)</p>\\s*<br>", "</p>");
    origHtml = origHtml.replaceAll("(?is)<br>\\s*<p>", "<p>");
    origHtml = origHtml.replaceAll("(?is)<p>", "\n<p>").replaceAll("\n\n+", "\n");

    // Make sure images are surrounded by <p> tags. Duplicates will be removed later.
    origHtml = origHtml.replaceAll("(?i)\\s*(<img[^>]*>)", "\n<p>$1</p>");
    String html;
    do {
      html = origHtml;
      // Remove nested <p>..</p> tags, especially if we introduced them around images.
      html = html.replaceAll("<p>\\s*<p>", "<p>");
      html = html.replaceAll("</p>\\s*</p>", "</p>");
      // Remove &nbsp; before or after <br> or <p> tags
      html = html.replaceAll("&nbsp;\\s*<(br|p|/p)>", "<$1>");
      html = html.replaceAll("<(br|p|/p)>\\s*&nbsp;", "<$1>");
      // Remove <br> before or after <p> tags
      html = html.replaceAll("<br>\\s*<(/?p)>", "<$1>");
      html = html.replaceAll("<(/?p)>\\s*<br>", "<$1>");
      html = html.replaceAll("</p>\\s*<p>\\s*</p>", "</p>");
      html = html.replaceAll("\n\n+", "\n");
      html = html.replaceAll("<span[^>]*>", "").replace("</span>", "");
      html = html.replaceAll("<font[^>]*>", "").replace("</font>", "");
      html = html.replaceAll("<div[^>]*>", "<p>").replace("</div>", "</p>");
    } while (!html.equals(origHtml));
    // Clean up newlines around <p> tags
    html = html.replaceAll("\\s*<p>\\s*", "\n<p>")
        .replaceAll("\\s*</p>\\s*", "</p>\n")
        .replaceAll("\\s*\n", "\n")
        .replaceAll("\n\n+", "\n")
        .replaceAll("<p>\\s*<p>", "<p>")
        .replaceAll("</p>\\s*</p>", "</p>");

    System.out.println("=== Formatted HTML ==>");
    System.out.println(html);
    System.out.println();
    return html;
  }

  private static String removeNonNestedDivs(String body) {
    body = removeOuterDivs(body);
    int divPos = body.indexOf("<div");
    String lowerBody = body.toLowerCase();
    while (divPos >= 0) {
      int divTagEnd = body.indexOf(">", divPos);
      if (divTagEnd < 0) {
        break; // Malformed tag, stop processing
      }
      int endDivPos = lowerBody.indexOf("</div>", divPos);
      int nextDivPos = lowerBody.indexOf("<div", divPos + 1);
      if (endDivPos > 0 && (nextDivPos < 0 || endDivPos < nextDivPos)) {
        // Found matching </div>
        String divContent = body.substring(divTagEnd + 1, endDivPos);
        body = body.substring(0, divPos) + "<p>" + divContent + "</p>" + body.substring(endDivPos + 6);
        lowerBody = body.toLowerCase();
        divPos = lowerBody.indexOf("<div");
      } else {
        // No matching </div>, or else nested <div> found first; Leave as-is.
        break;
      }
    }
    return body;
  }

  /**
   * Removes outer <div> tags when they wrap only a single inner <div> tag and nothing else (except whitespace).
   * Handles multiple levels of nesting by counting inner <div> tags.
   * Example: <div>   <div>...</div>   </div> => <div>...</div>
   */
  private static String removeOuterDivs(String body) {
    boolean changed;
    do {
      changed = false;
      int start = 0;
      StringBuilder sb = new StringBuilder();
      while (true) {
        int outerOpen = body.indexOf("<div", start);
        if (outerOpen < 0) {
          sb.append(body.substring(start));
          break;
        }
        int outerTagEnd = body.indexOf('>', outerOpen);
        if (outerTagEnd < 0) {
          sb.append(body.substring(start));
          break;
        }
        int outerClose = body.indexOf("</div>", outerTagEnd);
        if (outerClose < 0) {
          sb.append(body.substring(start));
          break;
        }
        String inner = body.substring(outerTagEnd + 1, outerClose).trim();
        // Count inner <div> and </div>
        int divCount = 0;
        int pos = 0;
        while (pos < inner.length()) {
          int openDiv = inner.indexOf("<div", pos);
          int closeDiv = inner.indexOf("</div>", pos);
          if (openDiv >= 0 && (openDiv < closeDiv || closeDiv < 0)) {
            divCount++;
            pos = openDiv + 4;
          } else if (closeDiv >= 0) {
            divCount--;
            pos = closeDiv + 6;
          } else {
            break;
          }
        }
        // If exactly one inner <div> and it matches one </div>, and nothing else but whitespace
        if (divCount == 0 && inner.replaceAll("(?is)<div.*?</div>", "").trim().isEmpty()) {
          // Remove outer <div> tags
          sb.append(body.substring(start, outerOpen));
          sb.append(inner);
          start = outerClose + 6;
          changed = true;
        } else {
          sb.append(body.substring(start, outerClose + 6));
          start = outerClose + 6;
        }
      }
      body = sb.toString();
    } while (changed);
    return body;
  }
}
