package com.bigclue.archive;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlCleanerTest {

  @Test
  void testKeepActualLineBreaks() {
    tryKeepingActualLineBreaks("Once upon a time,<br> there was a test.", "Once upon a time,<ß>there was a test.");
    tryKeepingActualLineBreaks("<p>Once upon a time,<br> there was a test.<br></p> <div>\n   <br>\n </div>", "<p>Once upon a time,<ß>there was a test.<br></p> <div>\n   <br>\n </div>");
  }

  private void tryKeepingActualLineBreaks(String input, String expected) {
    String actual = HtmlCleaner.replaceActualLineBreaks(input);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void testFormat() {
    tryFormat("""
            <p><img src="platypus1-overview.jpg"></p>
            <p></p>
            <p>Here's an overview of the Pinewood Platypus.</p>
            """,
        """
            <p><img src="platypus1-overview.jpg"></p>
            <p>Here's an overview of the Pinewood Platypus.</p>
            """);

    tryFormat("""
            <p><img src="DSC_0869.jpg"></p>
            
            <p><img src="DSC_0870.jpg"></p>
            
            """,
        """
            <p><img src="DSC_0869.jpg"></p>
            <p><img src="DSC_0870.jpg"></p>
            """);

    tryFormat("""
            <div><div style="margin-top: 0px; margin-right: 0px; margin-bottom: 0px; margin-left: 0px; "><font class="Apple-style-span" size="3"><span class="Apple-style-span" style="font-size: 12px;"><span class="Apple-style-span" style="font-size: medium;">Here are a couple of shots of Jerusalem, and also a picture of me with the 10 kids that we had.</span></span></font></div><div style="margin-top: 0px; margin-right: 0px; margin-bottom: 0px; margin-left: 0px; "><br></div><div style="margin-top: 0px; margin-right: 0px; margin-bottom: 0px; margin-left: 0px; font: normal normal normal 12px/normal Helvetica; color: rgb(0, 0, 0); min-height: 14px; "><img src="IMG_0224.jpg"></div><div style="margin-top: 0px; margin-right: 0px; margin-bottom: 0px; margin-left: 0px; font: normal normal normal 12px/normal Helvetica; color: rgb(0, 0, 0); min-height: 14px; "><br></div><div style="margin-top: 0px; margin-right: 0px; margin-bottom: 0px; margin-left: 0px; font: normal normal normal 12px/normal Helvetica; color: rgb(0, 0, 0); min-height: 14px; "><img src="IMG_0225.jpg"></div><div style="margin-top: 0px; margin-right: 0px; margin-bottom: 0px; margin-left: 0px; font: normal normal normal 12px/normal Helvetica; color: rgb(0, 0, 0); min-height: 14px; "><br></div><div style="margin-top: 0px; margin-right: 0px; margin-bottom: 0px; margin-left: 0px; font: normal normal normal 12px/normal Helvetica; color: rgb(0, 0, 0); min-height: 14px; "><img src="IMG_0226.jpg"></div><div style="margin-top: 0px; margin-right: 0px; margin-bottom: 0px; margin-left: 0px; font: normal normal normal 12px/normal Helvetica; color: rgb(0, 0, 0); min-height: 14px; "><br></div><div style="margin-top: 0px; margin-right: 0px; margin-bottom: 0px; margin-left: 0px; font: normal normal normal 12px/normal Helvetica; color: rgb(0, 0, 0); min-height: 14px; "><img src="IMG_0230.jpg"></div></div>
            
            """,
        """
            <p>Here are a couple of shots of Jerusalem, and also a picture of me with the 10 kids that we had.</p>
            <p><img src="IMG_0224.jpg"></p>
            <p><img src="IMG_0225.jpg"></p>
            <p><img src="IMG_0226.jpg"></p>
            <p><img src="IMG_0230.jpg"></p>
            """);

    tryFormat("""
            Oscar,<div><br></div><div>My name is Randy Wilson
            """,
        """
            <p>Oscar,</p>
            <p>My name is Randy Wilson</p>
            """);

    tryFormat("""
            <div><b>Adventures in Guatemala, Part I</b></div><div><i>In Which Randy &amp; Linette visit old friends...</i></div><div><br></div>As many of you have heard...<div><br></div><div><br></div><div>
            """,
        """
            <p><b>Adventures in Guatemala, Part I</b></p>
            <p><i>In Which Randy &amp; Linette visit old friends...</i></p>
            <p>As many of you have heard...</p>
            """);

    tryFormat("""
            Randy,&nbsp;<div><br></div><div>WELL DONE! &nbsp;I especially love the buttons!...</div><div><br></div><div>Dad Wilson<br><div><br><div>Begin forwarded message:</div><br class="Apple-interchange-newline"><blockquote type="cite"><div style="margin-top: 0px; margin-right: 0px; margin-bottom: 0px; margin-left: 0px; "><font face="Helvetica" size="3" color="#000000" style="font: 12.0px Helvetica; color: #000000"><b>From: </b></font><font face="Helvetica" size="3" style="font: 12.0px Helvetica">Randy Wilson &lt;<a href="mailto:randywilson99@gmail.com">randywilson99@gmail.com</a>&gt;</font></div><div style="margin-top: 0px; margin-right: 0px; margin-bottom: 0px; margin-left: 0px; ">October 30, 2012 4:50:07 PM PDT</div> </blockquote></div><br></div>
            
            """,
        """
            <p>Randy,</p>
            <p>WELL DONE! &nbsp;I especially love the buttons!...</p>
            <p>Dad Wilson</p>
            <p>Begin forwarded message:</p>
            <p><b>From: </b>Randy Wilson &lt;<a href="mailto:randywilson99@gmail.com">randywilson99@gmail.com</a>&gt;</p>
            <p>October 30, 2012 4:50:07 PM PDT</p>
            """);

    tryFormat("""
            Once there was a poem<br>that had a line break.<br><br>It was a good poem.
            """,
        """
            <p>Once there was a poem<br>
            that had a line break.</p>
            <p>It was a good poem.</p>
            """);

    tryFormat("""
            <div>We stayed with Janette &amp; Brandon Pace...</div><div><br></div><div>We saw a lot of animals...</div><div><br></div><div><img src="2014-09-13_10-55-12_DRW_7584.jpeg"><img src="2014-09-13_11-45-06_DRW_7604.jpeg"><img src="2014-09-13_12-18-19_DRW_7618.jpeg"><img src="2014-09-13_14-16-10_Erika_2615.jpeg"><img src="2014-09-13_14-46-00_DRW_7710.jpeg"><img src="2014-09-13_15-40-12_DRW_7723.jpeg"><img src="2014-09-13_15-54-34_DRW_7731.jpeg"><img src="2014-09-13_16-40-31_DRW_7780.jpeg"><img src="2014-09-13_18-09-49_Erika_2628.jpeg"></div><div><br></div><div>On the way back...</div><div><br></div><div><img src="2014-09-13_20-23-39_DRW_7857.jpeg"></div><div><br></div><div>Sunday was Erika’s birthday! &nbsp;</div><div><br></div><div><img src="2014-09-14_08-53-56_DRW_7871.jpeg"><img src="2014-09-14_17-31-29_DRW_7893.jpeg"><img src="2014-09-14_17-46-58_Linette_1186.jpeg"></div><div><br></div><div>We enjoyed California...</div><div><br></div><div>Of course...</div><div><br></div><div>Love, Dad</div>
            """,
        """
            <p>We stayed with Janette &amp; Brandon Pace...</p>
            <p>We saw a lot of animals...</p>
            <p><img src="2014-09-13_10-55-12_DRW_7584.jpeg"></p>
            <p><img src="2014-09-13_11-45-06_DRW_7604.jpeg"></p>
            <p><img src="2014-09-13_12-18-19_DRW_7618.jpeg"></p>
            <p><img src="2014-09-13_14-16-10_Erika_2615.jpeg"></p>
            <p><img src="2014-09-13_14-46-00_DRW_7710.jpeg"></p>
            <p><img src="2014-09-13_15-40-12_DRW_7723.jpeg"></p>
            <p><img src="2014-09-13_15-54-34_DRW_7731.jpeg"></p>
            <p><img src="2014-09-13_16-40-31_DRW_7780.jpeg"></p>
            <p><img src="2014-09-13_18-09-49_Erika_2628.jpeg"></p>
            <p>On the way back...</p>
            <p><img src="2014-09-13_20-23-39_DRW_7857.jpeg"></p>
            <p>Sunday was Erika’s birthday!</p>
            <p><img src="2014-09-14_08-53-56_DRW_7871.jpeg"></p>
            <p><img src="2014-09-14_17-31-29_DRW_7893.jpeg"></p>
            <p><img src="2014-09-14_17-46-58_Linette_1186.jpeg"></p>
            <p>We enjoyed California...</p>
            <p>Of course...</p>
            <p>Love, Dad</p>
            """);
  }

  @Test
  void testKeepLinksAroundImages() {
    tryFormat("""
        <div class='separator'>
          <a href="full/1955_MHM_03_0890.jpg" style="margin-left: 1em; margin-right: 1em;"><img border="0" height="432" src="small/1955_MHM_03_0890.jpg" width="640"></a>
        </div>
        """,
        """
        <p><a href="full/1955_MHM_03_0890.jpg" style="margin-left: 1em; margin-right: 1em;"><img border="0" height="432" src="small/1955_MHM_03_0890.jpg" width="640"></a></p>
        """);
  }

  @Test
  void testParagraphsAroundTables() {
    tryFormat("""
        <p><table align="center" cellpadding="0" cellspacing="0" class="tr-caption-container" style="margin-left: auto; margin-right: auto; text-align: center;">
          <tbody>
           <tr>
            <td style="text-align: center;">
            <a href="full/2015-12-31_13-44-41_IMG_8514.JPG" imageanchor="1" style="margin-left: auto; margin-right: auto;"></p>
              <p><img border="0" height="480" src="small/2015-12-31_13-44-41_IMG_8514.JPG" width="640"></p>
        <p></a></td>
           </tr>
           <tr>
            <td class="tr-caption" style="text-align: center;"></p>
        <p></td>
           </tr>
          </tbody>
         </table> More stuff</p>""",
        """
        <p><table class="tr-caption-container">
          <tbody>
           <tr>
            <td style="text-align: center;">
            <a href="full/2015-12-31_13-44-41_IMG_8514.JPG" imageanchor="1" style="margin-left: auto; margin-right: auto;"><img border="0" height="480" src="small/2015-12-31_13-44-41_IMG_8514.JPG" width="640"></a></td>
           </tr>
           <tr>
            <td class="tr-caption" style="text-align: center;"></td>
           </tr>
          </tbody>
         </table></p>
        <p>More stuff</p>
        """);
  }
  private void tryFormat(String input, String expected) {
    String actual = HtmlCleaner.cleanBody(input);
    assertThat(actual).isEqualTo(expected);
  }
}
