package com.example.commons.accounts.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.example.commons.accounts.review.ReviewDtos.PopulationEntryResponse;
import com.example.commons.accounts.review.ReviewReportModel.ItemRow;
import com.example.commons.accounts.review.ReviewReportModel.ItemSection;
import com.example.commons.accounts.review.ReviewReportModel.PopulationSection;
import com.example.commons.accounts.review.ReviewReportModel.Summary;

/**
 * Tests the report files: one consolidated csv with a category column, a sheet for each
 * category in the xlsx, and a pdf.
 */
class DefaultReviewReportRendererTest {

	private static final Instant CREATED = Instant.parse("2026-01-05T08:00:00Z");

	private static final Instant LOGIN = Instant.parse("2026-10-01T09:30:00Z");

	private static final Instant SUSPENDED = Instant.parse("2026-09-01T10:00:00Z");

	private static final Instant DECIDED = Instant.parse("2026-10-15T10:00:00Z");

	private final DefaultReviewReportRenderer renderer = new DefaultReviewReportRenderer();

	@Test
	void theCsvHoldsTheActiveSuspendedAndRemovedAccountsUnderOneHeader() {
		String csv = new String(this.renderer.csv(model()), StandardCharsets.UTF_8).replace("﻿", "");

		List<String> lines = csv.lines().toList();
		assertThat(lines).hasSize(4);
		assertThat(lines.get(0)).isEqualTo("\"Category\",\"No.\",\"Name\",\"Username\",\"Department\",\"Created\","
				+ "\"Last Login\",\"Roles\",\"Suspended / Removed On\",\"Suspended / Removed By\",\"Reason\","
				+ "\"Review Outcome\",\"Remarks\",\"Review Date\",\"Confirmed By\",\"Confirmed On\"");
		assertThat(lines.get(1)).isEqualTo("\"Active\",\"1\",\"Alice\",\"alice\",\"Finance\",\"5 Jan 2026 08:00\","
				+ "\"1 Oct 2026 09:30\",\"users\",\"\",\"\",\"\",\"Confirmed\",\"No changes\",\"15 Oct 2026 10:00\","
				+ "\"\",\"\"");
		assertThat(lines.get(2)).isEqualTo("\"Suspended\",\"1\",\"Bob\",\"bob\",\"HR\",\"5 Jan 2026 08:00\","
				+ "\"1 Oct 2026 09:30\",\"users\",\"1 Sep 2026 10:00\",\"admin\",\"other\",\"Pending\","
				+ "\"Suspension note: away\",\"\",\"\",\"\"");
		assertThat(lines.get(3))
			.isEqualTo("\"Removed\",\"1\",\"Carol\",\"carol\",\"IT\",\"\",\"1 Oct 2026 09:30\",\"\","
					+ "\"1 Sep 2026 10:00\",\"admin\",\"left_organisation\",\"\",\"resigned\",\"\",\"rachel\","
					+ "\"15 Oct 2026 10:00\"");
	}

	@Test
	void theXlsxHasASheetForEachCategory() throws Exception {
		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(this.renderer.xlsx(model())))) {
			assertThat(workbook.getSheetName(0)).isEqualTo("Summary");
			assertThat(workbook.getSheetName(1)).isEqualTo("Active accounts");
			assertThat(workbook.getSheetName(2)).isEqualTo("Suspended accounts");
			assertThat(workbook.getSheetName(3)).isEqualTo("Removed accounts");
			assertThat(workbook.getSheet("Suspended accounts").getRow(0).getLastCellNum()).isEqualTo((short) 13);
			assertThat(workbook.getSheet("Suspended accounts").getRow(1).getCell(7).getStringCellValue())
				.isEqualTo("1 Sep 2026 10:00");
		}
	}

	@Test
	void thePdfIsRendered() {
		byte[] pdf = this.renderer.pdf(model());

		assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
	}

	private static ReviewReportModel model() {
		ItemRow active = new ItemRow(1, "Alice", "alice", "Finance", CREATED, LOGIN, "users", null, null, null,
				"Confirmed", "No changes", DECIDED);
		ItemRow suspended = new ItemRow(1, "Bob", "bob", "HR", CREATED, LOGIN, "users", SUSPENDED, "admin", "other",
				"Pending", "Suspension note: away", null);
		PopulationEntryResponse removed = new PopulationEntryResponse(java.util.UUID.randomUUID(), "carol", "Carol",
				"IT", null, LOGIN, null, SUSPENDED, "admin", "left_organisation", "resigned");
		return new ReviewReportModel(false, true, ZoneOffset.UTC, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
				LocalDate.of(2026, 10, 31), DECIDED, "rachel", DECIDED, "rachel",
				new ItemSection(new Summary(1, 0, 0, 0, 1), List.of(), List.of(active)),
				new ItemSection(new Summary(0, 0, 0, 1, 1), List.of(), List.of(suspended)),
				new PopulationSection(true, "rachel", DECIDED, null, List.of(removed)));
	}

}
