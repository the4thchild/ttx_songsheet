/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is the Text Trix code.
 *
 * The Initial Developer of the Original Code is
 * Text Flex.
 * Portions created by the Initial Developer are Copyright (C) 2003-6
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s): David Young <dvd@textflex.com>
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package com.textflex.texttrix;

import javax.swing.*;
import java.io.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

/** Customizes song sheets for musical performance or distribution.
 * For now the two main features are transposing chords and taking 
 * sheets with both chords and 
 * lyrics and stripping out the chords.  Worship leaders can then 
 * customize the chords to their desired voice range and convert their 
 * musical song sheets into lyrics for the audience to
 * sing along, for example.
*/
public class Plug extends PlugInWindow {
	
	/* Constants */
	// generally accepted represenation of the flat sign
	private static final String FLAT = "b"; 

	private SongSheetDialog diag = null; // the GUI dialog window
	private String chordIndicatorsList = ""; // list of chord suffixes
	private int threshold = 2; // num of chords to check, if possible
	private boolean transpose = false; // flag for transpose mode
	private int transposeSteps = 0; // number of full steps to transpose up
	// flag to transpose an extra half step up
	private boolean transposeHalfStep = false; 
	private boolean selectedRegion = false; // flag for selected region only
	private static final String chords = "ABCDEFG"; // chords
	// list of chords, stepping by one starting with A
	private static final String fullChords[] = {
		"A", "A#", "B", "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#"
	};
	// list of chords, stepping by one starting with Ab
	private static final String fullChordsFlat[] = {
		"Ab", "A", "Bb", "B", "C", "Db", "D", "Eb", "E", "F", "Gb", "G"
	};

	/** Constructs the extra returns remover with descriptive text and 
	images.
	*/
	public Plug() {
		super(
			"Song Sheet Maker",
			"trix",
			"Converts song sheets",
			"desc.html",
			"icon.png",
			"icon-roll.png");
		setAlwaysEntireText(true); // retrieve the entire body of text

		// Runs the plug-in if the user hits "Enter" in components with this adapter
		KeyAdapter removerEnter = new KeyAdapter() {
			public void keyPressed(KeyEvent evt) {
				if (evt.getKeyCode() == KeyEvent.VK_ENTER) {
					runPlugIn();
				}
			}
		};


		// Runs the plug-in if the user hits the "Song Sheet Maker"
		// button;
		// creates a shortcut key (alt-S) as an alternative way to invoke
		// the button
		Action songSheetAction = 
			new AbstractAction("Song Sheet Maker", null) {
			public void actionPerformed(ActionEvent e) {
				applyUserOptions();
				runPlugIn();
			}
		};
		LibTTx.setAcceleratedAction(
			songSheetAction,
			"Song Sheet Maker",
			'S',
			KeyStroke.getKeyStroke("alt S"));

		// Creates the options dialog window
		diag =
			new SongSheetDialog(
				removerEnter,
				songSheetAction);
		setWindow(diag);
	}
	
	/** Applies the user-defined options from the graphical interface.
	*/
	public void applyUserOptions() {
		chordIndicatorsList = diag.getChordIndicatorsList();
		threshold = diag.getThreshold();
		transpose = diag.getTranspose();
		transposeSteps = diag.getSteps();
		transposeHalfStep = diag.getHalfStep();
		selectedRegion = diag.getSelectedRegion();
	}

	/** Gets the normal icon.
	@return normal icon
	*/
	public ImageIcon getIcon() {
		return getIcon(getIconPath());
	}

	/** Gets the rollover icon.
	@return rollover icon
	*/
	public ImageIcon getRollIcon() {
		return getRollIcon(getRollIconPath());
	}

	/** Gets the detailed, HTML-formatted description.
	For display as a tool tip.
	@return a buffered reader for the description file
	*/
	public BufferedReader getDetailedDescription() {
		return super.getDetailedDescription(getDetailedDescriptionPath());
	}
	
	/** Runs the song sheet.
	 * Performs the same function as run(String, int, int).
	 * @pararm s text
	 * @see #run(String, int, int)
	 * @return converted text
	*/
	public PlugInOutcome run(String s) {
		return run(s, 0, 0);
	}
	
	/**Runs the song sheet maker on the text, following the options
	 * set in the dialogue window.
	 * Assumes that these options have been recorded in the class
	 * fields.
	 * Features TODO include splitting the method into chord transposer,
	 * chord remover, and other functions.
	 * @param s the string to remove extraneous returns from
	 * @param x the starting index of any selected region, taken as the 
	 * starting position to work on, but ignored
	 * if the "selectedArea" option is unchecked
	 * @param y the final index of any selected region, taken as the 
	 * ending position, noninclusive, on which to work, but ignored if
	 * the "selectedArea" option is unchecked
	 * @return the text, clean, washed, and ready
	*/
	public PlugInOutcome run(String s, int x, int y) {
		/* This function works by generally checking the characters afer
		 * a hard return to determine whether to keep it or not.
		 * To strip inline message reply characters, the function must also
		 * check the beginning of the string separately.  Additionally, the
		 * function completely excludes "<pre>"-tag-delimited areas from hard
		 * return removal.
		 */
		
		/* Indices */
		int n = x; // string index
		int end = y;
		// resets the indices to work on the entire text if the selected region
		// option is unchecked
		if (!selectedRegion) {
			n = 0;
			end = s.length();
		}
		// number of words to check per line, defaulting to 1000
		int appliedThreshold = (threshold == 0) ? 1000 : threshold;
		
		/* Flags, storage, and symbols */
		StringBuffer stripped = new StringBuffer(end - n); // storage string buffer
		stripped.append(s.substring(0, n)); // add preceding lines if selected area
		int lineBreak = 0; // end of the line, line break non-inclusive
		String line = ""; // the current line
		// strings that can follow a chord family name
		String[] chordIndicators = createArrayFromList(chordIndicatorsList);
		String transposedLine = ""; // the current working line
		int linesChanged = 0; // records num of lines changed
		
		/* Removes chord lines */
		while (n < end) {
			// finds the end of the line, whether defined by an "\n" or the end
			// of the text
			lineBreak = s.indexOf("\n", n);
			line = (lineBreak != -1) ? s.substring(n, lineBreak) : s.substring(n);
			
			// tracks the position within the line, starting after blankspace
			int offset = 0;
			int linePos = countBlankspace(line, 0);
			boolean chordLine = false; // flags chordal lines
			
			// Determines if the line is chordal
			int count = 0; // records num of chords checked
			// Cycles through works in a line until determines that not chordal
			do {
				if (!line.substring(linePos).equals("")) {
					chordLine = isChord(line, linePos, chordIndicators);
				}
				// skips to next word
				linePos = nextWordInLinePos(line, linePos);
			} while (++count < appliedThreshold 
				&& chordLine && linePos != -1 && linePos < line.length());
			// continues only until checked only user-defined num of chords,
			// all words so far have been chords, and the end of the line has not 
			// been reached
			
			// retains the line if not flagged as chordal or if user-chosen transpose
			if (!chordLine) {
				stripped.append(line + "\n");
				linesChanged++;
			} else if (transpose) {
				transposedLine = transposeLine(line);
				stripped.append(transposedLine + "\n");
				linesChanged++;
			}
			// advances to next line
			n += line.length() + 1;
			
		}
		
		if (transpose) {
			displayResults(new String[] { linesChanged + " lines transposed" }, 1);
		} else {
			displayResults(new String[] { linesChanged + " lines deposed" }, 1);
		}
		
		// Creates the output string, appending the rest of the text if left over
		// after selected region
		String strippedStr = stripped.toString();
		if (n < s.length()) strippedStr += s.substring(n);
		return new PlugInOutcome(strippedStr);
					
	}
	
	/** Determines if a word is a chord.
	 * Chords are defined as words that begin with a capitalized chord
	 * family name, including sharps (eg "A" or "A#"), followed by nothing,
	 * a digit, or one of a list of chord indicators (eg "sus", "aug").
	 * @param line the entire line in which the word resides
	 * @param linePos the position of the word within the line
	 * @param chordIndicators array of strings that follow the chord family
	 * name and indicate chords
	 * @return true if the word is a chord
	*/
	public boolean isChord(String line, int linePos, String[] chordIndicators) {
		// finds chords according to the criteria:
		// -begins with a chord family name, in caps
		// -is followed by a chord indicator, a digit, blankspace, or nothing 
		// (end of line or file)
		return (linePos == -1
			|| (chords.indexOf(line.charAt(linePos)) != -1
				&& (linePos + 1 >= line.length()
					|| Character.isDigit(line.charAt(linePos + 1))
					|| strPosTest(line, linePos, 1, chordIndicators) != -1
					|| isBlankspace(line.charAt(linePos + 1)))));
	}
	
	/** Transposes the line.
	 * Shifts the chords according to user-defined number of steps via the gui.
	 * @param line the line of chords to transpose
	 * @return the transposed line
	*/
	public String transposeLine(String line) {
		// buffer for the newly transposing line
		StringBuffer strBuffer = new StringBuffer(line.length());
		// flag whether a char follows a blankspace, which would indicate
		// that the char is a chord family name
		boolean followsBlankspace = true;
		char c = 0; // the current character
		String chord = ""; // the current chord
		
		// Cycles through characters to find chords
		for (int n = 0; n < line.length(); n++) {
			// Skips over blankspaces and flags as such;
			// treats "/" and "(" as blankspaces to allow transposition of chords
			// placed in an alternate position
			if (isBlankspace(chord = line.substring(n, n + 1))
				|| chord.equals("/")
				|| chord.equals("(")) {
				followsBlankspace = true;
			} else if (followsBlankspace) {
				// converts non-blankspace chars that follow blankspaces:
				// chord family names;
				// includes the sharp sign if it immediately follows the char
				char halfStep = 0;
				if (n < line.length() - 1 
					&& ((halfStep = line.charAt(n + 1)) == '#') || halfStep == 'b') {
					chord = line.substring(n, n + 2);
					n++;
				}
				chord = transposeChord(chord, transposeSteps, transposeHalfStep);
				followsBlankspace = false;
			}
			strBuffer.append(chord);
		}
		return strBuffer.toString();
	}
	
	/** Transposes a single chord.
	 * Shifts the chord according to the user-defined number of steps.
	 * @param chord the chord to transpose
	 * @param steps the number of whole steps to increment
	 * @param halfStep flags whether a half-step should be added to the
	 * total number of steps to transpose (eg if 2 steps plus half-step, then
	 * 2.5 whole steps', or 5 notes' increase)
	 * @return the transposed chord; "--" if the chord family isn't found
	*/
	public String transposeChord(String chord, int steps, boolean halfStep) {
		boolean isFlat = chord.indexOf("b") == 1;
//		System.out.println("chord: " + chord + ", isFlat: " + isFlat);
		String[] chordList = isFlat ? fullChordsFlat : fullChords;
		int totSteps = 2 * steps; // the total number of steps to shift
		// adds a half-step if applicable //, subtracting if the steps are negative
		if (halfStep)
			totSteps += 1; //+= (steps >= 0) ? 1 : -1;
		int chordPos = -1; // the current chord position, set to -1 b/c of initial increment
		// locates the chord in the array of chord families, which include sharps
		while (++chordPos < chordList.length && !chord.equals(chordList[chordPos]));
		
		// Returns blank if chord not found
		if (chordPos >= chordList.length) return "--";
		chordPos += totSteps;
		// Wraps around to first chord family and continues to cycle through families
		// until reaches the specified number of steps
		while (chordPos < 0) chordPos += chordList.length;
		while (chordPos >= chordList.length) chordPos -= chordList.length;
		return chordList[chordPos];
	}
	
	/** Tests whether any of an array of strings is found at
	 * a given position within another string
	 * @param s the string to search in
	 * @param offset the position at which to start looknig for strings from the array
	 * @param pos the number of positions past the offset at which to find
	 * the strings; if negative, will always return false
	 * @param strTests strings to find in s
	 * @return true if any of the strings in the array are found at offset + pos
	 * in s
	*/
	public int strPosTest(String s, int offset, int pos, String[] strTests) {
		// check to see if any of the elements in the array are found at 
		// offset + pos within the search string;
		// returns true as soon as any are found
		for (int n = 0; n < strTests.length; n++) {
			if ((s.indexOf(strTests[n], offset) == offset + pos)) return n;
		}
		return -1;
	}
	
	/** Counts the number of blankspace--tabs or spaces--that
	 * start at and are continuous from the given position.
	 * @param s the string to search
	 * @param offest the position at which to start searching
	 * @return the number of blankspaces
	*/
	public int countBlankspace(String s, int offset) {
		int n = 0;
		// counts the number of blankspaces starting at offset and
		// continuing unbroken
		while (offset + n < s.length() && isBlankspace(s, offset + n)) n++;
		return n;
	}
	
	/** Counts the number of non-blankspaces--neither tabs nor spaces--that
	 * start at and are continuous from the given position.
	 * @param s the string to search
	 * @param offest the position at which to start searching
	 * @return the number of non-blankspaces
	*/
	public int countNonBlankspace(String s, int offset) {
		int n = 0;
		// counts the number of non-blankspaces starting at offset and
		// continuing unbroken
		while (offset + n < s.length() && !isBlankspace(s, offset + n)) n++;
		return n;
	}
	
	/** Checks if the character at the given position is a space or tab.
	 * @param s the string to search
	 * @param offset the position to check
	 * @return true if the character is a blankspace
	*/
	public boolean isBlankspace(String s, int offset) {
		char c = s.charAt(offset);//0;
		return isBlankspace(c);//test;
	}
	
	/** Checks if the character is a space or tab.
	 * @param c the character to check
	 * @return true if the character is a space or tab.
	*/
	public boolean isBlankspace(char c) {
		return c == ' ' || c == '\t' || c == '\240';// || c == '(';
	}
	
	/** Checks if the first character of the given string is a space or tab.
	 * @param string the string to check
	 * @return true if the first character is a space or tab.
	*/
	public boolean isBlankspace(String s) {
		return isBlankspace(s.charAt(0));
	}
	
	/** Finds the position of the next word in a line.
	 * First passes over non-blankspaces, then blankspaces to reach
	 * the beginning of the next word, skipping over any characters
	 * in the current word.
	 * @param s the line to search
	 * @param offset the position at which to start searching
	 * @return the position of the next word; -1 if no words left, 
	 * such as at the end of the line
	*/
	public int nextWordInLinePos(String s, int offset) {
		String line = "";
		// first skips non-blankspaces
		int n = countNonBlankspace(s, offset);
		// then skips subsequent blankspaces
		n += countBlankspace(s, offset + n);
		// bringing position to next non-blankspace
		return (offset + n >= s.length()) ? -1 : offset + n;
	}
	
	/** Finds the  next word in a line.
	 * First passes over non-blankspaces, then blankspaces to reach
	 * the beginning of the next word, skipping over any characters
	 * in the current word.
	 * @param s the line to search
	 * @param offset the position at which to start searching
	 * @return the next word; "" if no words left, 
	 * such as at the end of the line
	*/
	public String nextWordInLine(String s, int offset) {
		int n = nextWordInLinePos(s, offset);
		return (n < 0 || n >= s.length()) ? "" : s.substring(n);
	}
	
	/** Creates an array from a comma-delimited string.
	 * @param list comma-delimited string
	 * @return an string array
	*/
	private String[] createArrayFromList(String list) {
		StringTokenizer tok = new StringTokenizer(list, ",");
		String[] array = new String[tok.countTokens()];
		int n = 0;
		while (tok.hasMoreTokens()) {
			array[n++] = tok.nextToken();
		}
		return array;
	}

	
	/**Storage class for list markers.
	 * Contains the marker as well as a flag for whether the marker
	 * is associated with an outline symbol, where "[outline]" flags
	 * such symbols.  For example, "-" or "*" are not outline
	 * symbols, whereas "IV" or "ix" or "a" could serve as these
	 * symbols.  If the user submits the marker, "[outline])", ")' is
	 * considered the marker, while "[outline]" flags the outline
	 * field.
	*/
	private class ListLookup {
		private String marker = ""; // list marker
		private boolean outline = false; // flags outline marker
		// user generic symbol for outline incrementors, eg 
		// "IV","A", or "3"
		private String outlineStr = "[outline]";
		// length of the generic outline symbol
		private int outlineStrLen = outlineStr.length();
		
		/**Creates a marker storage object.
		 * Checks for outline symbols.
		 * @param aMarker the marker to store; pared down
		 * to the symbols following the generic "[outline]"
		 * designation if appropriate
		*/
		public ListLookup(String aMarker) {
			marker = aMarker;
			// pares down and flags outline markers
			if (aMarker.toLowerCase().startsWith(outlineStr)) {
				outline = true;
				marker = aMarker.substring(outlineStrLen);
			}
		}
		
		/**Gets the marker.
		 * @return the marker
		*/
		public String getMarker() { return marker; }
		/**Gets the outline flag.
		 * @return outline flag, <code>true</code> if the originally set
		 * marker started with "[outline]"
		*/
		public boolean getOutline() { return outline; }
	}

	/** Finds the first continuous string consisting of any of a given
	set of chars and returns the sequence's length if it contains any of 
	another given set of chars.
	@param seq string to search
	@param start <code>seq</code>'s index at which to start searching
	@param chars chars for which to search in <code>seq</code>
	@param innerChars required chars to return the length of the first
	continuous string of chars from <code>chars</code>; if no
	<code>innerChars</code> are found, returns 0
	 */
	public int containingSeq(
		String seq,
		int start,
		String chars,
		String innerChars) {
		char nextChar;
		boolean inSeq = false;
		int i = start;
		while (seq.length() > i
			&& chars.indexOf(nextChar = seq.charAt(i)) != -1) {
			i++;
			if (innerChars.indexOf(nextChar) != -1) {
				inSeq = true; // set flag that found a char from innerChar
			}
		}
		return (inSeq) ? i - start : 0;
	}
	
	/** Displays the results of the transposition or chord removal, such
	 * as the number of lines updated.
	 * @param results the various results statements
	 * @param weightFront how much to weight statements at the fron
	 * of the results array
	 */
	private void displayResults(String[] results, int weightFront) {
		diag.setResultsLbl(LibTTx.pickWeightedStr(results, weightFront));
	}
	
}

/** Find and replace dialog.
    Creates a dialog box accepting input for search and replacement 
    expressions as well as options to tailor the search.
*/
class SongSheetDialog extends JPanel {//JFrame {

	private static final String DEFAULT_CHORD_INDICATORS = "#,b,/,sus,aug,dim,m";
	
	JLabel tips = null; // offers tips on using the plug-in 
	JLabel chordIndicatorsListLbl = null; // label for the search field
	JTextField chordIndicatorsListFld = null; // search expression input
	JLabel thresholdLbl = null; // label for the replacement field
	SpinnerNumberModel thresholdMdl = null;
	JSpinner thresholdSpinner = null; // replacement expression input
	JLabel stepLbl = null;
	SpinnerNumberModel stepsMdl = null;
	JLabel stepsLbl = stepsLbl = null;
	JSpinner stepsSpinner = null;
	JCheckBox halfStepChk = null;
	JRadioButton transposeRad = null; // reply boundaries
	JRadioButton chordRemoverRad = null;
	JCheckBox selectedRegionChk = null; // only work on selected region
	JLabel resultsTitleLbl = null; // intros the results
	JLabel resultsLbl = null; // shows the results
	JButton removerBtn = null; // label for the search button
	ButtonGroup songSheetTypeGrp = new ButtonGroup();

	/**Construct a find/replace dialog box
	 * @param owner frame to which the dialog box will be attached; 
	 * can be null
	 */
	public SongSheetDialog(
		KeyAdapter removerEnter,
		Action songSheetAction) {
		super(new GridBagLayout());
		setSize(350, 200);
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.anchor = GridBagConstraints.CENTER;
		String msg = "";

		// Threshold spinner
		
		JLabel thresholdLbl =
			new JLabel("Max chords to check per line:");
		String thresholdTipTxt =
			"<html>The maximum number of words/chords that will be checked"
				+ "<br>in a line before considering it a chordal line.  If the line ends"
				+ "<br>before reaching this number but otherwise meets the criteria"
				+ "<br>for chords, the line will be considered chordal.  Choose \"0\""
				+ "<br>to include all chords possible.</html>";
		thresholdLbl.setToolTipText(thresholdTipTxt);
		// houses the user-chosen value
		thresholdMdl =
			new SpinnerNumberModel(2, 0, 100, 1);
		thresholdSpinner = new JSpinner(thresholdMdl);

		stepsLbl =
			new JLabel("Full steps to transpose:");
		String stepsTipTxt =
			"<html>The maximum number of words/chords that will be checked"
				+ "<br>in a line before considering it a chordal line.  If the line ends"
				+ "<br>before reaching this number but otherwise meets the criteria"
				+ "<br>for chords, the line will be considered chordal.</html>";
		stepsLbl.setToolTipText(stepsTipTxt);
		// houses the user-chosen value
		stepsMdl =
			new SpinnerNumberModel(1, -6, 6, 1);
		stepsSpinner = new JSpinner(stepsMdl);

		// tips display; intros the plug-in
		tips = new JLabel("Welcome to Song Sheet, your textual song maker!");
		LibTTx.addGridBagComponent(
			tips,
			constraints,
			0,
			0,
			3,
			1,
			100,
			0,
			this);//contentPane);


		// Option to add reply email boundary markers
		transposeRad = new JRadioButton("Transpose", true);
		transposeRad.setEnabled(false);
		LibTTx.addGridBagComponent(
			transposeRad,
			constraints,
			0,
			1,
			1,
			1,
			100,
			0,
			this);//contentPane);
		transposeRad.setMnemonic(KeyEvent.VK_T);
		msg = "Transposes the chords";
		transposeRad.setToolTipText(msg);
		songSheetTypeGrp.add(transposeRad);
		transposeRad.setEnabled(true);
		transposeRad.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				setSteps(transposeRad.isSelected());
			}
		});
		
		// Option to add reply email boundary markers
		chordRemoverRad = new JRadioButton("Chord Removal");
		chordRemoverRad.setEnabled(false);
		LibTTx.addGridBagComponent(
			chordRemoverRad,
			constraints,
			1,
			1,
			2,
			1,
			100,
			0,
			this);//contentPane);
		chordRemoverRad.setMnemonic(KeyEvent.VK_C);
		msg = "Removes chords for sing-along sheets";
		chordRemoverRad.setToolTipText(msg);
		songSheetTypeGrp.add(chordRemoverRad);
		chordRemoverRad.setEnabled(true);
		/* Not necessary b/c transposeRad must change when
		 * chordRemoverRad is selected
		chordRemoverRad.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				setSteps(!chordRemover.isSelected());
			}
		});
		*/
		
		// user-defined, comma-delimited list of list markers
		chordIndicatorsListLbl = new JLabel("Chord suffixes:");
		msg =
			"<html>Characters that follow a chord family name to indicate that"
			+ "<br>the \"word\" is indeed a chord.  For example, if one indicator"
			+ "<br>is \"sus\", then \"Asus\" will be treated as a chord.</html>";
		chordIndicatorsListLbl.setToolTipText(msg);
		LibTTx.addGridBagComponent(
			chordIndicatorsListLbl,
			constraints,
			0,
			2,
			1,
			1,
			100,
			0,
			this);
		chordIndicatorsListFld = new JTextField(DEFAULT_CHORD_INDICATORS, 20); //"#,\t, ,\240,/,sus,aug,dim,m"
		LibTTx.addGridBagComponent(
			chordIndicatorsListFld,
			constraints,
			1,
			2,
			2,
			1,
			100,
			0,
			this);//contentPane);
		// pressing enter in the input field starts the remover
		chordIndicatorsListFld.addKeyListener(removerEnter);
		
		// Threshold placement
		LibTTx.addGridBagComponent(
			thresholdLbl,
			constraints,
			0,
			3,
			1,
			1,
			100,
			0,
			this);
		LibTTx.addGridBagComponent(
			thresholdSpinner,
			constraints,
			1,
			3,
			2,
			1,
			100,
			0,
			this);//contentPane);
		
		// Steps placement
		LibTTx.addGridBagComponent(
			stepsLbl,
			constraints,
			0,
			4,
			1,
			1,
			100,
			0,
			this);
		LibTTx.addGridBagComponent(
			stepsSpinner,
			constraints,
			1,
			4,
			1,
			1,
			100,
			0,
			this);//contentPane);
		halfStepChk = new JCheckBox("Half-step up");
		msg = 
			"<html>Adds a half-step to the number of steps to transpose.</html>";
		halfStepChk.setToolTipText(msg);
		LibTTx.addGridBagComponent(
			halfStepChk,
			constraints,
			2,
			4,
			1,
			1,
			100,
			0,
			this);//contentPane);
		
		
		// Option to work only within highlighted section
		selectedRegionChk = new JCheckBox("Selected area only");
		LibTTx.addGridBagComponent(
			selectedRegionChk,
			constraints,
			0,
			5,
			2,
			1,
			100,
			0,
			this);//contentPane);
		selectedRegionChk.setMnemonic(KeyEvent.VK_A);
		msg = "Removes extra returns only within the highlighted section";
		selectedRegionChk.setToolTipText(msg);
		
		// Displays the results of the removal
		resultsTitleLbl = new JLabel("Results: ");
		LibTTx.addGridBagComponent(
			resultsTitleLbl,
			constraints,
			0,
			6,
			1,
			1,
			100,
			0,
			this);//contentPane);
		resultsLbl = new JLabel("");
		resultsLbl.setHorizontalAlignment(JLabel.RIGHT);
		LibTTx.addGridBagComponent(
			resultsLbl,
			constraints,
			1,
			6,
			2,
			1,
			100,
			0,
			this);//contentPane);

		// fires the "Song Sheet" action
		removerBtn = new JButton(songSheetAction);
		LibTTx.addGridBagComponent(
			removerBtn,
			constraints,
			0,
			7,
			3,
			1,
			100,
			0,
			this);//contentPane);
	}
	
	/** Enables/disables the step controls.
	 * @param b true enables the step controls
	*/
	public void setSteps(boolean b) {
		stepsLbl.setEnabled(b);
		stepsSpinner.setEnabled(b);
		halfStepChk.setEnabled(b);
	}
	
	/**Gets the chord indicators.
	 * @return the list of chord indicators
	*/
	public String getChordIndicatorsList() { return chordIndicatorsListFld.getText(); }
	/**Gets the threshold.
	 * @return the minimum line length to remove returns from
	*/
	public int getThreshold() { return thresholdMdl.getNumber().intValue(); }
	
	/** Gets the number of steps.
	 * @return the number of steps
	*/
	public int getSteps() { return stepsMdl.getNumber().intValue(); }
	
	/** Gets the half-step flag.
	 * @return true if half-step should be added
	*/
	public boolean getHalfStep() { return halfStepChk.isSelected(); }
	
	/**Gets the email markers flag.
	 * @return the flag to add reply email boundary markers
	*/
	public boolean getTranspose() { return transposeRad.isSelected(); }
	
	/**Gets the selected region flag.
	 * @return flag to only work on the selected region
	*/
	public boolean getSelectedRegion() { return selectedRegionChk.isSelected(); }
	
	/** Sets the results label.
	 * @param s what to set the results label
	*/
	public void setResultsLbl(String s) {
		resultsLbl.setText(s);
	}

}