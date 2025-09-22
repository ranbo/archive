package com.bigclue.archive;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import static com.bigclue.archive.HtmlCleaner.cleanBody;
import static com.bigclue.archive.MailToHtml.log;
import static com.bigclue.archive.MailToHtml.sanitizeFilename;

@SuppressWarnings({"java:S106", "java:S1148"})
public class BlogToHtml {

  public static void main(String[] args) throws IOException {
    String blogUrl = "https://notabletimes.blogspot.com";
    Path outputDir = Paths.get("TravelBlog");
    System.out.println("Reading post list...");
    List<String> allPostUrls = getAllPostUrls(blogUrl);
    for (String postUrl : allPostUrls) {
      System.out.println(postUrl);
      saveBlogPost(postUrl, outputDir);
    }
  }

  /**
   * Fetches the main blog page and returns a list of all month archive URLs (e.g. .../2025/06/)
   */
  public static List<String> getMonthArchiveUrls(String blogUrl) throws IOException {
    List<String> monthUrls = new ArrayList<>();
    Document doc = Jsoup.connect(blogUrl).get();
    Elements links = doc.select("a.post-count-link");
    for (Element link : links) {
      String href = link.attr("href");
      // Match URLs like .../YYYY/MM/
      if (href.matches(".*/\\d{4}/\\d{2}/")) {
        monthUrls.add(href);
      }
    }
    return monthUrls;
  }

  /**
   * Given a month archive URL, fetches the page and returns a list of all post URLs for that month.
   */
  public static List<String> getPostUrlsForMonth(String monthUrl) throws IOException {
    List<String> postUrls = new ArrayList<>();
    Document doc = Jsoup.connect(monthUrl).get();
    Elements postLinks = doc.select("ul.posts li a");
    for (Element link : postLinks) {
      postUrls.add(link.attr("href"));
    }
    return postUrls;
  }

  /**
   * Returns a list of all blog post URLs from all months/years.
   */
  public static List<String> getAllPostUrls(String blogUrl) throws IOException {
    List<String> allPostUrls = new ArrayList<>();
    List<String> monthUrls = getMonthArchiveUrls(blogUrl);
    for (String monthUrl : monthUrls) {
      allPostUrls.addAll(getPostUrlsForMonth(monthUrl));
    }
    return allPostUrls;
  }

  /**
   * Fetches a blog post URL, extracts the date, title, and body, and writes the HTML to dirName/dirName.html.
   */
  public static void saveBlogPost(String postUrl, Path outputDir) throws IOException {
    Document doc = Jsoup.connect(postUrl).get();
    // Extract date
    Element dateHeader = doc.selectFirst("h2.date-header");
    String dateText = dateHeader != null ? dateHeader.text().trim() : "";
    String receivedDate = reformatDate(dateText);
    // Extract title
    Element h3 = doc.selectFirst("h3.post-title.entry-title");
    String articleTitle = h3 != null ? h3.text().trim() : postUrl.replaceAll(".*/", "");
    String dirName = receivedDate + "_" + MailToHtml.subjectToDirName(articleTitle);
    Path articleDir = outputDir.resolve(dirName);
    if (Files.exists(articleDir)) {
      log("## Skipping existing article: " + dirName);
      return;
    }
    Path tmpDir = outputDir.resolve("tmp." + dirName);
    Files.createDirectories(tmpDir);
    // Extract body
    Element body = doc.selectFirst("div.post-body.entry-content");
    fetchImages(body, tmpDir);

    String bodyHtml = cleanBody(body != null ? body.outerHtml() : "");
    // Build output HTML
    StringBuilder html = new StringBuilder();
    html.append("<html>\n<head>\n  <meta charset=\"UTF-8\">\n  <title>")
        .append(MailToHtml.escapeHtml(articleTitle))
        .append("</title>\n  <link rel='stylesheet' type='text/css' href='../blog.css'>\n</head>\n<body>\n");
    if (!dateText.isEmpty()) {
      html.append("<h3 class='date-header'>").append(MailToHtml.escapeHtml(dateText)).append("</h3>\n");
    }
    html.append("<h2 class='entry-title'><a href='").append(postUrl).append("'>")
        .append(MailToHtml.escapeHtml(articleTitle)).append("</a></h2>\n");

    html.append(bodyHtml);
    html.append("</body></html>");
    // Directory name
    Path htmlFile = tmpDir.resolve(dirName + ".html");
    Files.writeString(htmlFile, html.toString(), StandardCharsets.UTF_8);
    Files.move(tmpDir, articleDir, StandardCopyOption.ATOMIC_MOVE);
  }

  private static int imageCounter = 1;

  private static void fetchImages(Element body, Path outputDir) {
    if (body == null) return;
    Elements anchors = body.select("a[href]");
    Path fullDir = outputDir.resolve("full");
    Path smallDir = outputDir.resolve("small");
    try {
      Files.createDirectories(fullDir);
      Files.createDirectories(smallDir);
    } catch (IOException e) {
      // Ignore or log
    }
    for (Element a : anchors) {
      String href = a.attr("href");
      Element img = a.selectFirst("img[src]");
      if (href.startsWith("https://blogger.googleusercontent.com/img/")) {
        String imageFilename = getImageFilename(href);
        Path localImage = fullDir.resolve(imageFilename);

        if (Files.exists(localImage)) {
          log("  ## Skipping existing file: " + imageFilename);
        } else {
          // Download the full image
          href = reEncodeUrl(href);
          try (java.io.InputStream in = new java.net.URL(href).openStream()) {
            Path tmpFile = fullDir.resolve("tmp." + imageFilename);
            Files.copy(in, tmpFile, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmpFile, localImage, StandardCopyOption.REPLACE_EXISTING);
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
        a.attr("href", "full/" + imageFilename);
      }
      if (img != null) {
        String src = img.attr("src");
        if (src.startsWith("https://blogger.googleusercontent.com/img/")) {
          String smallFilename = getImageFilename(src);
          Path localSmallImage = smallDir.resolve(smallFilename);

          if (Files.exists(localSmallImage)) {
            log("  ## Skipping existing file: " + smallFilename);
          } else {
            src = reEncodeUrl(src);
            // Download the small image
            try (java.io.InputStream in = new java.net.URL(src).openStream()) {
              Files.copy(in, smallDir.resolve("tmp." + smallFilename), StandardCopyOption.REPLACE_EXISTING);
              Files.move(smallDir.resolve("tmp." + smallFilename), localSmallImage, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
              // Ignore or log
            }
          }
          img.attr("src", "small/" + smallFilename);
        }
      }
    }
  }

  private static String getImageFilename(String url) {
    // Check if URL has a normal image filename (period followed by 3-4 chars at end)
    int lastSlash = url.lastIndexOf('/');
    if (lastSlash >= 0) {
      String urlPart = url.substring(lastSlash + 1);
      if (urlPart.matches(".*\\.[a-zA-Z]{3,4}$")) {
        return sanitizeFilename(urlPart);
      }
    }

    // Otherwise, get filename from Content-Disposition header
    try {
      java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
      try {
        connection.setRequestMethod("HEAD"); // Only get headers, not body
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setConnectTimeout(5000); // 5 second timeout
        connection.setReadTimeout(5000);

        String contentDisposition = connection.getHeaderField("Content-Disposition");
        if (contentDisposition != null && contentDisposition.contains("filename=")) {
          String filename = contentDisposition.substring(contentDisposition.indexOf("filename=") + 9);
          filename = filename.replaceAll("(^\")|(\"$)", "");
          return sanitizeFilename(filename);
        }

        // Fallback: generate name with proper extension from Content-Type
        String contentType = connection.getContentType();
        String extension = getExtensionFromContentType(contentType);
        return "image" + (imageCounter++) + extension;

      } finally {
        connection.disconnect(); // Explicitly close connection
      }
    } catch (Exception e) {
      return "image" + (imageCounter++) + ".jpg";
    }
  }

  private static String getExtensionFromContentType(String contentType) {
    if (contentType != null) {
      if (contentType.contains("png")) return ".png";
      if (contentType.contains("gif")) return ".gif";
      if (contentType.contains("webp")) return ".webp";
    }
    return ".jpg"; // default
  }

  // Re-encode URL to handle spaces and line breaks.
  // In particular, remove line breaks, and make sure spaces are %20, not literal spaces.
  private static String reEncodeUrl(String url) {
    try {
      // Remove line breaks but preserve spaces, then properly encode
      String cleanUrl = url.trim().replaceAll("\\r?\\n", "");
      java.net.URI uri = new java.net.URI(cleanUrl);
      return uri.toASCIIString();
    } catch (Exception e) {
      // Fallback: remove all whitespace since spaces in URLs are usually unintentional
      return url.trim().replaceAll("\\s+", "");
    }
  }

  private static String reformatDate(String dateText) {
    String receivedDate;
    try {
      SimpleDateFormat inFmt = new SimpleDateFormat("EEEE, MMMM d, yyyy");
      SimpleDateFormat outFmt = new SimpleDateFormat("yyyy-MM-dd");
      receivedDate = outFmt.format(inFmt.parse(dateText));
    } catch (Exception e) {
      receivedDate = dateText.replaceAll("[^\\d-]", "-"); // fallback
    }
    return receivedDate;
  }
}
