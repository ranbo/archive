package com.bigclue.archive;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MailToHtmlTest {
  @Test
  void testSubjectToDirName() {
    assertThat(MailToHtml.subjectToDirName("What's up: John's big news!")).isEqualTo("JohnsBigNews");
    assertThat(MailToHtml.subjectToDirName("What's up: Costa Rica 3-Water Island")).isEqualTo("CostaRica3-WaterIsland");
    assertThat(MailToHtml.subjectToDirName("Whats Up: José águila 2: up stuff o.k.")).isEqualTo("JoséÁguila2-UpStuffO.K");
    assertThat(MailToHtml.subjectToDirName("Wilson ReUnion 5: 25 people")).isEqualTo("WilsonReUnion5-25People");
    assertThat(MailToHtml.subjectToDirName("Whats Up - ...more stuff... ")).isEqualTo("MoreStuff");
  }

  @Test
  void testReplaceImageRef() {
    StringBuilder html = new StringBuilder();
    html.append("<body><p><img id=\"<idWithAngleBracketsToThrowYouOff>\" src=\"cid:B572CE0B-3994-4289-8875-BFC7257826F0\" alt=\"2023-10-25_15-53-02_IMG_3198.jpeg\"></p>");
    String replacement = "<img src=\"actual_image.jpeg\">";
    String target = "B572CE0B-3994-4289-8875-BFC7257826F0";
    assertThat(MailToHtml.replaceInlineImageTag(html, target, replacement)).isTrue();
    assertThat(html.toString()).hasToString("<body><p><img src=\"actual_image.jpeg\"></p>");
  }
}

