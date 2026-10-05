package com.example.commons.accounts.report;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

/**
 * A small base for building a PDF report with OpenPDF, so a report only composes its
 * sections: a title block, key-value metadata, summary tiles and tables. Every page gets
 * a footer with the page number and, for a draft, a banner saying it is not evidence.
 *
 * <p>
 * The built-in Helvetica font is used, which covers Western European characters; text
 * outside it, such as Chinese names, is not drawn. An application that needs it supplies
 * its own renderer.
 */
public final class ReportDocument {

	private static final Color INK = new Color(0x1f2937);

	private static final Color MUTED = new Color(0x6b7280);

	private static final Color RULE = new Color(0xd1d5db);

	private static final Color HEADER_FILL = new Color(0xf3f4f6);

	private static final Color DRAFT = new Color(0xb91c1c);

	/**
	 * A summary tile: a large value over a label.
	 *
	 * @param color the accent colour of the value
	 */
	public record Tile(String value, String label, Color color) {
	}

	private final Document document;

	private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

	/**
	 * Starts an A4 landscape report.
	 * @param title the title, shown at the top of the first page
	 * @param footer the text at the left of every page footer
	 * @param draft whether to mark every page as a draft
	 */
	public ReportDocument(String title, String footer, boolean draft) {
		this.document = new Document(PageSize.A4.rotate(), 36, 36, draft ? 54 : 40, 48);
		PdfWriter writer = PdfWriter.getInstance(this.document, this.bytes);
		writer.setPageEvent(new PageDecoration(footer, draft));
		this.document.open();
		Paragraph heading = new Paragraph(title, font(20, Font.BOLD, INK));
		heading.setSpacingAfter(8);
		this.document.add(heading);
	}

	/**
	 * Adds a section heading.
	 */
	public void heading(String text) {
		Paragraph heading = new Paragraph(text, font(13, Font.BOLD, INK));
		heading.setSpacingBefore(14);
		heading.setSpacingAfter(6);
		this.document.add(heading);
	}

	/**
	 * Adds a line of plain text.
	 */
	public void text(String text) {
		Paragraph paragraph = new Paragraph(text, font(9, Font.NORMAL, INK));
		paragraph.setSpacingAfter(4);
		this.document.add(paragraph);
	}

	/**
	 * Adds a muted note, such as an explanation under a table.
	 */
	public void note(String text) {
		Paragraph paragraph = new Paragraph(text, font(8, Font.ITALIC, MUTED));
		paragraph.setSpacingAfter(4);
		this.document.add(paragraph);
	}

	/**
	 * Adds labelled values, such as the review period and who generated the report. A
	 * null value is shown as a dash.
	 * @param pairs rows of {label, value}
	 */
	public void keyValues(List<String[]> pairs) {
		PdfPTable table = new PdfPTable(new float[] { 1, 4 });
		table.setWidthPercentage(60);
		table.setHorizontalAlignment(Element.ALIGN_LEFT);
		for (String[] pair : pairs) {
			table.addCell(cell(pair[0], font(9, Font.NORMAL, MUTED), Rectangle.NO_BORDER, null, 2));
			table.addCell(cell(pair[1] == null ? "-" : pair[1], font(9, Font.BOLD, INK), Rectangle.NO_BORDER, null, 2));
		}
		this.document.add(table);
	}

	/**
	 * Adds a row of summary tiles.
	 */
	public void tiles(List<Tile> tiles) {
		PdfPTable table = new PdfPTable(tiles.size());
		table.setWidthPercentage(100);
		table.setSpacingBefore(6);
		for (Tile tile : tiles) {
			PdfPCell cell = new PdfPCell();
			cell.setBorderColor(RULE);
			cell.setPadding(8);
			cell.setHorizontalAlignment(Element.ALIGN_CENTER);
			Paragraph value = new Paragraph(tile.value(), font(20, Font.BOLD, tile.color()));
			value.setAlignment(Element.ALIGN_CENTER);
			Paragraph label = new Paragraph(tile.label(), font(8, Font.NORMAL, MUTED));
			label.setAlignment(Element.ALIGN_CENTER);
			cell.addElement(value);
			cell.addElement(label);
			table.addCell(cell);
		}
		this.document.add(table);
	}

	/**
	 * Adds a table whose header row repeats on every page.
	 * @param headers the column headings
	 * @param widths the relative column widths
	 * @param rows the rows of cell text; a null cell is shown as a dash
	 */
	public void table(List<String> headers, float[] widths, List<String[]> rows) {
		PdfPTable table = new PdfPTable(widths);
		table.setWidthPercentage(100);
		table.setHeaderRows(1);
		table.setSpacingAfter(6);
		for (String header : headers) {
			table.addCell(cell(header, font(8, Font.BOLD, INK), Rectangle.BOX, HEADER_FILL, 4));
		}
		for (String[] row : rows) {
			for (String value : row) {
				table.addCell(cell(value == null || value.isEmpty() ? "-" : value, font(8, Font.NORMAL, INK),
						Rectangle.BOX, null, 4));
			}
		}
		this.document.add(table);
	}

	/**
	 * Finishes the document and returns the PDF.
	 */
	public byte[] toBytes() {
		this.document.close();
		return this.bytes.toByteArray();
	}

	private static PdfPCell cell(String text, Font font, int border, Color fill, float padding) {
		PdfPCell cell = new PdfPCell(new Phrase(text, font));
		cell.setBorder(border);
		cell.setBorderColor(RULE);
		cell.setPadding(padding);
		if (fill != null) {
			cell.setBackgroundColor(fill);
		}
		return cell;
	}

	private static Font font(float size, int style, Color color) {
		return FontFactory.getFont(FontFactory.HELVETICA, size, style, color);
	}

	/**
	 * Draws the footer and, for a draft, the banner on every page, and fills in the total
	 * page count when the document closes.
	 */
	private static final class PageDecoration extends PdfPageEventHelper {

		private final String footer;

		private final boolean draft;

		private PdfTemplate total;

		private BaseFont base;

		PageDecoration(String footer, boolean draft) {
			this.footer = footer;
			this.draft = draft;
		}

		@Override
		public void onOpenDocument(PdfWriter writer, Document document) {
			this.total = writer.getDirectContent().createTemplate(30, 12);
			try {
				this.base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
			}
			catch (java.io.IOException ex) {
				throw new DocumentException(ex);
			}
		}

		@Override
		public void onEndPage(PdfWriter writer, Document document) {
			PdfContentByte canvas = writer.getDirectContent();
			float left = document.left();
			float right = document.right();
			float y = document.bottom() - 24;
			canvas.setColorStroke(RULE);
			canvas.moveTo(left, y + 12);
			canvas.lineTo(right, y + 12);
			canvas.stroke();
			ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT, new Phrase(this.footer, font(8, Font.NORMAL, MUTED)),
					left, y, 0);
			String page = "Page " + writer.getPageNumber() + " of ";
			float pageWidth = this.base.getWidthPoint(page, 8);
			ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT, new Phrase(page, font(8, Font.NORMAL, MUTED)),
					right - pageWidth - 20, y, 0);
			canvas.addTemplate(this.total, right - 20, y);
			if (this.draft) {
				ColumnText.showTextAligned(canvas, Element.ALIGN_CENTER,
						new Phrase("DRAFT: generated from an open review, not audit evidence",
								font(10, Font.BOLD, DRAFT)),
						(left + right) / 2, document.top() + 18, 0);
			}
		}

		@Override
		public void onCloseDocument(PdfWriter writer, Document document) {
			this.total.beginText();
			this.total.setFontAndSize(this.base, 8);
			this.total.setColorFill(MUTED);
			this.total.setTextMatrix(0, 0);
			this.total.showText(String.valueOf(writer.getPageNumber() - 1));
			this.total.endText();
		}

	}

}
