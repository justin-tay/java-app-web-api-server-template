package com.example.commons.accounts.review;

/**
 * Lays an account review report out as a file. The default implementation uses OpenPDF
 * for the PDF and Apache POI for the workbook; an application can supply its own bean to
 * change the look or the libraries.
 */
public interface ReviewReportRenderer {

	/**
	 * Renders the report as a PDF.
	 */
	byte[] pdf(ReviewReportModel model);

	/**
	 * Renders the report as an xlsx workbook with one sheet per section.
	 */
	byte[] xlsx(ReviewReportModel model);

	/**
	 * Renders the active accounts as csv, one row per account.
	 */
	byte[] csv(ReviewReportModel model);

}
