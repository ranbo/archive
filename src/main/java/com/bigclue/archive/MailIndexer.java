package com.bigclue.archive;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static com.bigclue.archive.MailToHtml.escapeHtml;
import static com.bigclue.archive.MailToHtml.writeFileCarefully;
import static java.nio.file.Files.readString;

/**
 * Class to create an HTML file that has a list of all emails in a folder, with links to each email's HTML file.
 * The HTML file should be named index.html and be placed in the same folder as the emails.
 *
 */
public class MailIndexer {
  public static void main(String[] args) throws IOException {
    String inputDir = "TravelBlog"; // Directory containing one subdirectory per email message
    String outputFile = "index.html"; // Output HTML file
    List<Path> htmlFiles = getHtmlFiles(inputDir);
//    MailIndexer.linkEpisodes(htmlFiles);
    MailIndexer.createIndexHtml(inputDir, htmlFiles, outputFile);
  }

  private static void createIndexHtml(String inputDir, List<Path> htmlFiles, String outputFile) throws IOException {
    StringBuilder html = new StringBuilder();
    html.append("""
        <html>
        <head>
          <meta charset="UTF-8">
          <title>Email Index 1.3</title>
          <style>
            body { margin: 0; padding: 0; }
            .container { display: flex; height: 100vh; }
            .table-pane { flex-basis: 550px; flex-shrink: 0; flex-grow: 0; min-width: 200px; overflow-y: auto; }
            .divider { width: 5px; background: #ccc; cursor: ew-resize; position: relative; z-index: 10; }
            .iframe-pane { flex: 1 1 0; overflow-y: auto; }
            .year-row { background: #eee; font-weight: bold; cursor: pointer; }
            .width-keeper { visibility: collapse; height: 0 !important; padding: 0 !important; border: none !important; }
            .hidden { display: none; }
            .collapse-link { float: right; font-size: small; cursor: pointer; }
            a { text-decoration: none; }
            tr.selected { background: #d0eaff; }
            .date-cell { width: 80px; white-space: nowrap; }
          </style>
          <script>
            function toggleYear(year) {
              var rows = document.querySelectorAll('.row-' + year);
              var hidden = false;
              for (var i = 0; i < rows.length; i++) {
                if (!rows[i].classList.contains('hidden')) { hidden = true; break; }
              }
              for (var i = 0; i < rows.length; i++) {
                if (hidden) rows[i].classList.add('hidden'); else rows[i].classList.remove('hidden');
              }
            }
            function collapseAll() {
              var years = document.querySelectorAll('.year-row');
              years.forEach(function(row) {
                var year = row.getAttribute('data-year');
                var rows = document.querySelectorAll('.row-' + year);
                rows.forEach(function(r) { r.classList.add('hidden'); });
              });
            }
            function expandAll() {
              var years = document.querySelectorAll('.year-row');
              years.forEach(function(row) {
                var year = row.getAttribute('data-year');
                var rows = document.querySelectorAll('.row-' + year);
                rows.forEach(function(r) { r.classList.remove('hidden'); });
              });
            }
            function selectRow(row, htmlFile) {
              // Deselect all rows
              document.querySelectorAll('tr.selected').forEach(function(r) { r.classList.remove('selected'); });
              row.classList.add('selected');
              var iframe = document.getElementById('reading-pane');
              iframe.src = htmlFile;
            }
            // Divider drag logic
            window.onload = function() {
              var divider = document.getElementById('divider');
              var container = document.querySelector('.container');
              var tablePane = document.querySelector('.table-pane');
              var isDragging = false;
              var startX, startWidth;
              var overlay = document.createElement('div');
              overlay.style.position = 'fixed';
              overlay.style.top = '0';
              overlay.style.left = '0';
              overlay.style.width = '100vw';
              overlay.style.height = '100vh';
              overlay.style.zIndex = '9999';
              overlay.style.cursor = 'ew-resize';
              overlay.style.background = 'rgba(0,0,0,0)';
              divider.addEventListener('mousedown', function(e) {
                isDragging = true;
                document.body.style.cursor = 'ew-resize';
                startX = e.clientX;
                startWidth = tablePane.offsetWidth;
                document.body.appendChild(overlay);
                e.preventDefault();
              });
              document.addEventListener('mousemove', function(e) {
                if (!isDragging) return;
                var minWidth = 200;
                var maxWidth = container.offsetWidth - minWidth;
                var delta = e.clientX - startX;
                var newWidth = Math.min(Math.max(startWidth + delta, minWidth), maxWidth);
                tablePane.style.flexBasis = newWidth + 'px';
              });
              document.addEventListener('mouseup', function(e) {
                if (isDragging) {
                  isDragging = false;
                  document.body.style.cursor = '';
                  if (overlay.parentNode) overlay.parentNode.removeChild(overlay);
                }
              });
              // Keyboard navigation and escape
              document.addEventListener('keydown', function(e) {
                var selected = document.querySelector('tr.selected');
                var iframe = document.getElementById('reading-pane');
                if (e.key === 'Escape') {
                  if (selected) selected.classList.remove('selected');
                  iframe.src = '';
                } else if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
                  if (!selected) return;
                  e.preventDefault();
                  e.stopPropagation();
                  var rows = Array.from(document.querySelectorAll('tr[class^="row-"]'));
                  var currentIndex = rows.indexOf(selected);
                  var nextIndex = currentIndex;
                  if (e.key === 'ArrowUp') {
                    for (var i = currentIndex - 1; i >= 0; i--) {
                      if (rows[i]) { nextIndex = i; break; }
                    }
                  } else {
                    for (var i = currentIndex + 1; i < rows.length; i++) {
                      if (rows[i]) { nextIndex = i; break; }
                    }
                  }
                  if (nextIndex !== currentIndex) {
                    selected.classList.remove('selected');
                    var nextRow = rows[nextIndex];
                    nextRow.classList.add('selected');
                    var htmlFile = nextRow.getAttribute('onclick').match(/'([^']+)'/)[1];
                    iframe.src = htmlFile;
                    nextRow.scrollIntoView({block: 'nearest'});
                  }
                }
              });
            };
          </script>
        </head>
        <body>
        <div class='container'>
          <div class='table-pane'>
        """);
    html.append("<h2>").append(escapeHtml(inputDir)).append("</h2>\n");
    html.append("""
        <table border=1 cellpadding=4 cellspacing=0 style='width:100%'>
        <tr><th class="date-cell">Date</th><th>Subject</th></tr>
        <tr class='width-keeper'><td class="date-cell">2000-01-01</td><td>The world didn't end when Y2K hit.</td></tr>
        """);
    String lastYear = null;
    boolean firstYearRow = true;
    for (Path htmlFile : htmlFiles) {
      String dirName = htmlFile.getParent().getFileName().toString();
      String[] parts = dirName.split("_");
      if (parts.length < 2) {
        System.out.println("Could not parse message directory name: " + dirName);
        continue;
      }
      String date = parts[0];
      String year = date.substring(0, 4);
      if (!year.equals(lastYear)) {
        html.append("<tr class='year-row' data-year='").append(year).append("' onclick='toggleYear(\"").append(year).append("\")'>");
        html.append("<td colspan=2>").append(year);
        if (firstYearRow) {
          html.append(" <span class='collapse-link'>");
          html.append("<span onclick='event.stopPropagation();expandAll();'>Expand all</span> / ");
          html.append("<span onclick='event.stopPropagation();collapseAll();'>Collapse all</span>");
          html.append("</span>");
          firstYearRow = false;
        }
        html.append("</td></tr>\n");
        lastYear = year;
      }
      String escapedTitle = ""; // Already HTML escaped
      try {
        String fileHtml = Files.readString(htmlFile);
        int titleStartPos = fileHtml.indexOf("<title>");
        int titleEndPos = fileHtml.indexOf("</title>");
        if (titleStartPos >= 0 && titleEndPos > titleStartPos) {
          escapedTitle = fileHtml.substring(titleStartPos + 7, titleEndPos).trim();
        }
      } catch (IOException e) {
        escapedTitle = "<Error reading title>";
      }
      html.append("<tr class='row-").append(year).append("' onclick=\"selectRow(this, '").append(dirName).append("/").append(dirName).append(".html')\">");
      html.append("<td class=\"date-cell\">").append(date).append("</td>");
      html.append("<td><a href='")
          .append(dirName).append("/").append(dirName).append(".html' target='_blank'>")
          .append(escapedTitle).append("</a></td></tr>\n");
    }
    html.append("</table>\n</div>\n<div id='divider' class='divider'></div>\n<div class='iframe-pane'><iframe id='reading-pane' style='width:100%;height:100%;border:none;'></iframe></div>\n</div>\n</body>\n</html>\n");
    writeFileCarefully(Paths.get(inputDir, outputFile), html.toString());
  }

  /**
   * For each html file in the list, add a "Next episode" link at the end of the body,
   * linking to the next html file in the list, unless it is already there.
   * Skip any files that are a "Re:" (reply) message, identified by having "_Re-" in the filename.
   * @param htmlFiles - List of HTML files in chronological order
   */
  private static void linkEpisodes(List<Path> htmlFiles) throws IOException {
    List<Path> filteredFiles = removeReplyMessages(htmlFiles);
    for (int i = 0; i < filteredFiles.size() - 1; i++) {
      Path currentFile = filteredFiles.get(i);
      Path nextFile = filteredFiles.get(i + 1);
      addNextLink(currentFile, nextFile);
    }
  }

  private static List<Path> removeReplyMessages(List<Path> htmlFiles) {
    List<Path> filteredFiles = new ArrayList<>();
    for (Path file : htmlFiles) {
      if (!file.getFileName().toString().contains("_Re-")) {
        filteredFiles.add(file);
      }
    }
    return filteredFiles;
  }

  private static final String NEXT_EPISODE_MARKER = "<!-- Next episode link -->";

  private static void addNextLink(Path currentFile, Path nextFile) throws IOException {
    String currentHtml = readString(currentFile);
    String nextEpisodeTitle = extractTitle(readString(nextFile));
    int nextLinkPos = currentHtml.indexOf(NEXT_EPISODE_MARKER);
    int endBodyPos = currentHtml.indexOf("</body>");
    String nextLinkHtml = NEXT_EPISODE_MARKER + "\n" +
        "<hr>\n" +
        "<p>Next episode: <a href=\"../" + nextFile.getFileName() + "/" + nextFile.getFileName() + ".html\">" + escapeHtml(nextEpisodeTitle) + "</a></p>\n";
    if (nextLinkPos >= 0) {
      String existingNextLinkHtml = currentHtml.substring(nextLinkPos, endBodyPos).trim();
      if (!existingNextLinkHtml.equals(nextLinkHtml.trim())) {
        String updatedHtml = currentHtml.substring(0, nextLinkPos) + nextLinkHtml + currentHtml.substring(endBodyPos);
        writeFileCarefully(currentFile, updatedHtml);
      }
    }
    else {
      if (endBodyPos >= 0) {
        String updatedHtml = currentHtml.substring(0, endBodyPos) + "\n" + nextLinkHtml + currentHtml.substring(endBodyPos);
        writeFileCarefully(currentFile, updatedHtml);
      }
    }
  }

  private static String extractTitle(String html) {
    int titleStart = html.indexOf("<title>");
    int titleEnd = html.indexOf("</title>");
    if (titleStart != -1 && titleEnd != -1 && titleEnd > titleStart) {
      return html.substring(titleStart + 7, titleEnd).trim();
    }
    return "<No Title>";
  }

  private static List<Path> getHtmlFiles(String inputDir) {
    Path dir = Paths.get(inputDir);
    List<Path> htmlFiles = new ArrayList<>();
    try (DirectoryStream<Path> subdirs = Files.newDirectoryStream(dir, Files::isDirectory)) {
      List<Path> sortedSubdirs = new ArrayList<>();
      for (Path subdir : subdirs) {
        sortedSubdirs.add(subdir);
      }
      sortedSubdirs.sort(Path::compareTo);
      for (Path subdir : sortedSubdirs) {
        Path htmlFile = subdir.resolve(subdir.getFileName() + ".html");
        if (!Files.exists(htmlFile)) {
          System.err.println("Warning: HTML file not found for subdirectory: " + subdir);
        } else {
          htmlFiles.add(htmlFile);
        }
      }
    } catch (IOException e) {
      // Ignore or log error
    }
    return htmlFiles;
  }
}
