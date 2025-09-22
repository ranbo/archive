package com.bigclue.archive;

import jakarta.mail.*;
import jakarta.mail.internet.MimeBodyPart;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.Random;

import static java.nio.file.Files.writeString;

/**
 * Class to archive a Gmail mailbox to a directory. For each message, create a directory with a name based on
 *   the received date and subject. In that directory, create an HTML file with the message body,
 *   download all attachments (images), and link to them in the HTML.
 * You'll need to create an App Password for your Google account to use instead of your regular password.
 */
@SuppressWarnings({"java:S106", "java:S112", "java:S110", "java:S1148", "java:S1192"})
public class MailToHtml {
  public static void main(String[] args) {
    try {
      String email = null;
      String appPassword = null;
      String journalDir = "Documents/Journal";
      String mailbox = "What's Up";
      boolean shouldSkipExisting = true;
      for (int i = 0; i < args.length; i++) {
        switch (args[i]) {
          case "-email" -> email = args[++i];
          case "-password" -> appPassword = args[++i];
          case "-mailbox" -> mailbox = args[++i];
          case "-journalDir" -> journalDir = args[++i];
          case "-redo" -> shouldSkipExisting = false;
          default -> {
            System.err.println("Usage: MailToHtml [-email your-email] [-password app-specific-password] [-mailbox mailbox-name] [-journalDir journal-directory] [-redo]");
            return;
          }
        }
      }
      Store store = getStore(email, appPassword);
      String rootDir = System.getProperty("user.home") + "/" + journalDir + "/" + subjectToDirName(journalDir);
      Folder folder = store.getFolder(mailbox);
      folder.open(Folder.READ_ONLY);

      java.text.SimpleDateFormat dateTimeFormat = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
      Message[] messages = folder.getMessages();
      for (Message message : messages) {
        String receivedDate = dateTimeFormat.format(message.getReceivedDate());
        String subject = message.getSubject();
        if (subject == null || subject.trim().isEmpty()) {
          subject = "No Subject";
        }
        String dirName = receivedDate + "_" + subjectToDirName(subject);
        String path = rootDir + File.separator + dirName;
        System.out.println(path + "    <=    " + subject);
        if (shouldSkipExisting && new File(path).exists()) {
          continue; // Skip if directory already exists
        }
        String tempPath = path + ".tmp";
        File tempPathFile = new File(tempPath);
        if (!tempPathFile.exists()) {
          tempPathFile.mkdirs(); 
        }
        saveMessageAsHtmlWithAttachments(message, tempPathFile.toPath(), dirName, subject);
        if (!new File(tempPath).renameTo(new File(path))) {
          throw new IOException("Failed to rename " + tempPath + " to " + path);
        }
      }

      folder.close(false);
      store.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Saves the message as an HTML file in the given directory, downloads all attachments (images),
   * and links to them in the HTML. Handles filename collisions. Text is formatted with <p>, <b>, <i>.
   */
  public static void saveMessageAsHtmlWithAttachments(Message message, Path dir, String entryName, String subject) throws Exception {
    Path origHtmlFile = dir.resolve(entryName + ".orig.html");
    Path htmlFile = dir.resolve(entryName + ".html");
    String origHtml;
    if (Files.exists(origHtmlFile)) {
      // If original HTML file exists, read it and reformat rather than re-downloading the message's parts.
      origHtml = Files.readString(origHtmlFile);
    }
    else {
      StringBuilder htmlSb = new StringBuilder();
      String title = subject.replaceAll("(?i)what'?s up:?", "").trim();
      htmlSb.append("<html>\n<head>\n  <meta charset=\"UTF-8\">\n  <title>").append(escapeHtml(title)).append("</title>\n</head>\n<body>\n");
      processPart(message, dir, htmlSb);
      htmlSb.append("</body>\n</html>\n");
      origHtml = htmlSb.toString();
      writeFileCarefully(origHtmlFile, origHtml);
    }

    String cleanHtml = HtmlCleaner.cleanHtml(origHtml);
    writeFileCarefully(htmlFile, cleanHtml);
  }



  private static String getHtmlBody(String htmlText) {
    if (htmlText == null) {
      return "";
    }
    // Remove <head>...</head>
    String body = htmlText.replaceAll("(?is)<head[^>]*>.*?</head>", "");
    // Remove <html> and </html>
    body = body.replaceAll("(?is)<html[^>]*>", "");
    body = body.replaceAll("(?is)</html>", "");
    // Remove <body> and </body> (with any attributes)
    body = body.replaceAll("(?is)<body[^>]*>", "");
    body = body.replaceAll("(?is)</body>", "");
    return body.trim();
  }

  // Recursively process a Part (Message or BodyPart)
  private static void processPart(Part part, Path dir, StringBuilder html) throws Exception {
    if (part.isMimeType("text/plain")) {
      String text = (String) part.getContent();
      html.append(textToHtml(text));
    } else if (part.isMimeType("text/html")) {
      String htmlText = (String) part.getContent();
      String sanitizedHtml = getHtmlBody(htmlText);
      html.append(sanitizedHtml);
      log(sanitizedHtml);
    } else if (part.isMimeType("multipart/alternative")) {
      processAlternatives(part, dir, html);
    } else if (part.isMimeType("multipart/*")) {
      Multipart mp = (Multipart) part.getContent();
      for (int i = 0; i < mp.getCount(); i++) {
        processPart(mp.getBodyPart(i), dir, html);
      }
    } else if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || part.getFileName() != null) {
      saveAttachment(part, dir, html);
    } else {
      // Unknown part type, ignore or log
      System.out.println("Ignoring unknown part of type: " + part.getContentType());
    }
  }

  private static void processAlternatives(Part part, Path dir, StringBuilder html) throws Exception {
    // For alternative parts, prefer HTML over plain text, but also handle multipart/mixed or other multiparts
    Multipart mp = (Multipart) part.getContent();
    Part htmlPart = null;
    Part textPart = null;
    Part multipartPart = null;
    for (int i = 0; i < mp.getCount(); i++) {
      Part subPart = mp.getBodyPart(i);
      if (subPart.isMimeType("text/html")) {
        htmlPart = subPart;
      } else if (subPart.isMimeType("text/plain")) {
        textPart = subPart;
      } else if (subPart.isMimeType("multipart/*")) {
        multipartPart = subPart;
      } else {
        System.out.println("Ignoring unknown subpart of type: " + subPart.getContentType());
      }
    }
    // Prefer multipart (e.g., mixed) over HTML, then plain text
    if (multipartPart != null) {
      processPart(multipartPart, dir, html);
    } else if (htmlPart != null) {
      processPart(htmlPart, dir, html);
    } else if (textPart != null) {
      processPart(textPart, dir, html);
    }
  }

  private static void saveAttachment(Part part, Path dir, StringBuilder html) throws Exception {
    // Save attachment
    String filename = part.getFileName();
    if (filename == null) {
      filename = "attachment";
    }
    filename = sanitizeFilename(filename);
    filename = writeFile((MimeBodyPart) part, dir, filename);
    if (isImage(part)) {
      log("  ## Image: " + filename);
      if (!replaceInlineImage(part.getHeader("Content-ID"), filename, html)) {
        html.append("\n<p><img src=\"").append(filename).append("\"></p>\n");
      }
    } else {
      log("  ## Attachment: " + filename);
      html.append("\n<p><a href=\"").append(filename).append("\">Attachment: ").append(filename).append("</a></p>>\n");
    }
  }

  private static boolean replaceInlineImage(String[] header, String filename, StringBuilder html) {
    if (header != null && header.length > 0) {
      String contentId = header[0];
      String replacement = "<img src=\"" + filename + "\">";
      return replaceInlineImageTag(html, contentId, replacement);
    }
    return false;
  }

  /**
   * Replaces the <img ... src="cid:TARGET_ID" ...> tag with replacementString, handling quoted attributes robustly.
   */
  public static boolean replaceInlineImageTag(StringBuilder html, String contentId, String replacementString) {
    if (contentId.startsWith("<") && contentId.endsWith(">")) {
      // Remove surrounding angle brackets, if present
      contentId = contentId.substring(1, contentId.length() - 1);
    }
    int srcPos = findSrcPosition(html, contentId);
    if (srcPos < 0) {
      return false;
    }
    // Find last <img before srcPos
    int imgPos = html.lastIndexOf("<img", srcPos);
    if (imgPos < 0) {
      return false;
    }
    // Scan forward from imgPos to find closing '>', skipping any > inside quotes
    int closingBracketPos = findClosingBracket(html, imgPos);
    if (closingBracketPos > srcPos) {
      // Found end of tag
      String before = html.substring(0, imgPos);
      String after = html.substring(closingBracketPos + 1);
      html.setLength(0);
      html.append(before).append(replacementString).append(after);
      return true;
    }
    return false;
  }

  private static int findSrcPosition(StringBuilder html, String targetId) {
    String srcPattern1 = "src=\"cid:" + targetId + "\"";
    int srcPos = html.indexOf(srcPattern1);
    if (srcPos < 0) {
      String srcPattern2 = "src='cid:" + targetId + "'";
      srcPos = html.indexOf(srcPattern2);
    }
    return srcPos;
  }

  private static int findClosingBracket(StringBuilder html, int imgPos) {
    int i = imgPos;
    boolean inQuote = false;
    char quoteChar = 0;
    while (i < html.length()) {
      char c = html.charAt(i);
      if (inQuote) {
        if (c == quoteChar) {
          inQuote = false;
        }
      } else {
        if (c == '"' || c == '\'') {
          inQuote = true;
          quoteChar = c;
        } else if (c == '>') {
          return i;
        }
      }
      i++;
    }
    return -1;
  }

  /**
   * Write the attachment to file, handling filename collisions by either (a) noting that it's the same file and skipping,
   * or (b) renaming with _2, _3, etc. if different files have the same name.
   * @param part - E-mail part to save
   * @param dir - Directory to save into
   * @param filename - Filename to use (unless it collides with a different file of the same name)
   * @return The original file if no collision, or the new filename if renamed.
   * @throws Exception - If an exception occurs
   */
  private static String writeFile(MimeBodyPart part, Path dir, String filename) throws Exception {
    // Stream the MimeBodyPart to a byte array, in case it needs to be compared with an existing file of the same name
    if (filename.toLowerCase().endsWith(".tif") || filename.toLowerCase().endsWith(".tiff")) {
      // Convert TIFF to JPEG
      byte[] tiffBytes;
      try (InputStream partStream = part.getInputStream()) {
        tiffBytes = partStream.readAllBytes();
      }
      byte[] jpegBytes = convertTiffToJpeg(tiffBytes);
      String newFilename = filename.replaceAll("(?i)\\.tiff?$", ".jpg");
      return writeFileUnlessDuplicate(dir, newFilename, jpegBytes);
    }

    if (Files.exists(dir.resolve(filename))) {
      log("  ## Skipping existing file: " + filename);
      return filename;
    }
    byte[] partBytes;
    try (InputStream partStream = part.getInputStream()) {
      partBytes = partStream.readAllBytes();
    }
    return writeFileUnlessDuplicate(dir, filename, partBytes);
  }

  private static String writeFileUnlessDuplicate(Path dir, String filename, byte[] partBytes) throws IOException {
    String newFilename = resolveFilenameCollision(dir, filename);
    if (!newFilename.equals(filename)) {
      if (sameAsExistingFile(dir, filename, partBytes)) {
        // Same file, so skip writing
        log("  ## Skipping identical attachment: " + filename);
        return filename;
      } else {
        log("  ## Renaming attachment due to collision: " + filename + " => " + newFilename);
      }
    }
    writeBytes(dir, newFilename, partBytes);
    return newFilename;
  }

  private static void writeBytes(Path dir, String imageFilename, byte[] jpegBytes) throws IOException {
    Path tempFile = dir.resolve("tmp." + imageFilename);
    Files.write(tempFile, jpegBytes); // Create or truncate
    Files.move(tempFile, dir.resolve(imageFilename), StandardCopyOption.REPLACE_EXISTING);
  }

  private static boolean sameAsExistingFile(Path dir, String filename, byte[] partBytes) throws IOException {
    byte[] existingBytes = Files.readAllBytes(dir.resolve(filename));
    return java.util.Arrays.equals(partBytes, existingBytes);
  }

  static void log(String message) {
    System.out.println(message);
  }

  // Convert plain text to HTML, handling <p>, <b>, <i>
  private static String textToHtml(String text) {
    // Replace line breaks with <p>
    String[] paras = text.split("(\r?\n){2,}");
    StringBuilder sb = new StringBuilder();
    for (String para : paras) {
      String htmlPara = para.trim()
          .replaceAll("\r?\n", "<br>")
          .replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>") // **bold**
          .replaceAll("\\*(.+?)\\*", "<i>$1</i>"); // *italic*
      sb.append("<p>").append(htmlPara).append("</p>\n");
    }
    return sb.toString();
  }

  // Sanitize filename for filesystem
  static String sanitizeFilename(String name) {
    String sanitized = name.replace("%20", "_")
        .replaceAll("[^a-zA-Z0-9._-]", "_")
        .replaceAll("[.]_+", ".")
        .replaceAll("__+", "_");
    if (!sanitized.equals(name)) {
      System.out.println("Sanitized filename: " + name + " => " + sanitized);
    }
    return sanitized;
  }

  // Handle filename collisions by appending _2, _3, etc.
  private static String resolveFilenameCollision(Path dir, String filename) {
    String base = filename;
    String ext = "";
    int dot = filename.lastIndexOf('.');
    if (dot > 0) {
      base = filename.substring(0, dot);
      ext = filename.substring(dot);
    }
    int count = 2;
    String candidate = filename;
    while (Files.exists(dir.resolve(candidate))) {
      candidate = base + "_" + count + ext;
      count++;
    }
    return candidate;
  }

  // Check if filename is an image
  private static boolean isImage(Part part) {
    try {
      String contentType = part.getContentType();
      if (contentType != null && contentType.split(";")[0].toLowerCase().trim().startsWith("image/")) {
          return true;
        }

    } catch (MessagingException e) {
      // Fall back to filename check if we can't get content type
    }

    // Fallback to filename extension check
    String filename;
    try {
      filename = part.getFileName();
    } catch (MessagingException e) {
      return false;
    }

    if (filename == null) {
      return false;
    }
    return isImageByExtension(filename);
  }

  private static boolean isImageByExtension(String filename) {
    String lower = filename.toLowerCase();
    return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
        lower.endsWith(".heic") || lower.endsWith(".png") ||
        lower.endsWith(".gif") || lower.endsWith(".bmp") ||
        lower.endsWith(".webp") || lower.endsWith(".tif") ||
        lower.endsWith(".tiff") || lower.endsWith(".svg");
  }

  /**
   * Converts an email subject to a directory-friendly string:
   * - Removes "What's up:"
   * - TitleCases each word
   * - Removes spaces and punctuation
   * Example: "What's up: John's big news!" -> "JohnsBigNews"
   */
  public static String subjectToDirName(String subject) {
    if (subject == null) return "";
    // Remove "What's up:" (case-insensitive, with or without apostrophe)
    String title = subject.replaceAll("(?i)what'?s up:?", "")
        .replace("'", "") // Remove apostrophes
        .replaceAll("[?!()]", "") // Remove common 'boundary' punctuation
        .replaceAll("[:;~]", "-") // Replace common 'separator' punctuation with hyphen (when problematic with dir names)
        .replaceAll("[^-.\\p{IsAlphabetic}\\d]", " ")
        .replaceAll("^[-. ]+", "") // Remove leading dots or dashes
        .replaceAll("[-. ]+$", "") // Remove trailing dots or dashes
        .trim();
    // Split into words, TitleCase each word, and join them, while preserving hyphens and periods.
    StringBuilder sb = new StringBuilder();
    for (String word : title.split("\\s+")) {
      if (!word.isEmpty()) {
        word = handleDotsAndDashes(word, sb);
        if (!word.isEmpty()) {
          sb.append(Character.toUpperCase(word.charAt(0)));
        }
        if (word.length() > 1) {
          sb.append(word.substring(1));
        }
      }
    }
    return sb.toString();
  }

  private static String handleDotsAndDashes(String word, StringBuilder sb) {
    int pos = findNextDotOrDash(word);
    while (pos >= 0) {
      if (pos > 0) {
        sb.append(Character.toUpperCase(word.charAt(0)));
        if (pos > 1) {
          sb.append(word, 1, pos);
        }
      }
      sb.append(word.charAt(pos));
      word = word.substring(pos + 1);
      pos = findNextDotOrDash(word);
    }
    return word;
  }

  private static int findNextDotOrDash(String word) {
    int pos = word.indexOf('.');
    int dashPos = word.indexOf('-');
    if (dashPos >= 0 && (pos < 0 || dashPos < pos)) {
      pos = dashPos;
    }
    return pos;
  }

  private static Store getStore(String emailAddress, String appSpecificPassword) throws MessagingException {
    String host = "imap.gmail.com";
    Credentials creds = readCredentials(emailAddress, appSpecificPassword);
    String username = creds.email();
    String password = creds.password();

    Properties props = new Properties();
    props.put("mail.store.protocol", "imaps");
    props.put("mail.imaps.host", host);
    props.put("mail.imaps.port", "993");
    props.put("mail.imaps.ssl.enable", "true");

    Session session = Session.getInstance(props);
    Store store = session.getStore("imaps");
    store.connect(host, username, password);
    return store;
  }

  public static String unmap(String orig) {
    orig = orig.trim();
    Random rnd = new Random(42);
    if (orig.startsWith("^")) {
      int v = rnd.nextInt(orig.length() - 1) % 42;
      StringBuilder sb = new StringBuilder();
      for (char c : orig.substring(1).toCharArray()) {
        int r = rnd.nextInt(42);
        int m = v + c;
        m -= r;
        if (m < 33) {
          m += 94;
        }
        if (m > 126) {
          m -= 94;
        }
        sb.append((char) m);
      }
      return sb.toString();
    }
    else {
      return orig;
    }
  }

  public static Credentials readCredentials(String emailAddress, String appSpecificPassword) {
    if (emailAddress != null && appSpecificPassword != null) {
      return new Credentials(emailAddress, appSpecificPassword);
    }
    String home = System.getProperty("user.home");
    String path = home + "/data/db/keys/gm";
    try {
      java.util.List<String> lines = Files.readAllLines(Paths.get(path));
      if (lines.size() < 2) throw new IOException("Not enough lines in credentials file");
      return new Credentials(lines.get(0).trim(), unmap(lines.get(1).trim()));
    } catch (IOException e) {
      throw new RuntimeException("Failed to read credentials: " + e.getMessage(), e);
    }
  }

  public record Credentials(String email, String password) {}

  /**
   * Converts a TIFF image byte array to a JPEG image byte array.
   * Requires TwelveMonkeys ImageIO plugin for TIFF support.
   * Handles alpha channel by converting to TYPE_INT_RGB.
   */
  private static byte[] convertTiffToJpeg(byte[] tiffBytes) throws IOException {
    try (ByteArrayInputStream bais = new ByteArrayInputStream(tiffBytes)) {
      BufferedImage image = ImageIO.read(bais);
      if (image == null) throw new IOException("Could not read TIFF image");
      // Convert to TYPE_INT_RGB to remove alpha channel if present
      BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
      rgbImage.getGraphics().drawImage(image, 0, 0, null);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ImageIO.write(rgbImage, "jpg", baos);
      return baos.toByteArray();
    }
  }

  /**
   * Writes a file carefully by first writing to a temp file and then moving it to the target location.
   * @param file - Target file path
   * @param content - Content to write
   * @throws IOException - If an I/O error occurs
   */
  static void writeFileCarefully(Path file, String content) throws IOException {
    Path tempFile = Files.createTempFile("temp", ".html");
    writeString(tempFile, content, StandardCharsets.UTF_8);
    Files.move(tempFile, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
  }


  /**
   * Escapes HTML special characters in a string.
   */
  public static String escapeHtml(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
  }
}