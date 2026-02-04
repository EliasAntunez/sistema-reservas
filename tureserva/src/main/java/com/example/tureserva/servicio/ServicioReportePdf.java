package com.example.tureserva.servicio;

import com.example.tureserva.servicio.dto.ReporteFinancieroTemporalDTO;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPTable;
// PdfWriter imported fully-qualified where needed
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
// avoid importing java.awt.Font to prevent name clash with com.lowagie.text.Font
import java.awt.Paint;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.title.TextTitle;
import java.awt.RenderingHints;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.labels.ItemLabelPosition;
import org.jfree.chart.labels.ItemLabelAnchor;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.List;

@Service
public class ServicioReportePdf {
    private final Logger logger = LoggerFactory.getLogger(ServicioReportePdf.class);

    public void generarReporteFinancieroPdf(List<ReporteFinancieroTemporalDTO> datos, HttpServletResponse response,
                                            String generadoPor, String nombreComplejo,
                                            java.time.LocalDate inicio, java.time.LocalDate fin,
                                            BigDecimal totalIngresos, Long totalReservas,
                                            BigDecimal promedioIngresoDiario, BigDecimal ticketPromedio,
                                            long diasConDatos, long diasPeriodo) {
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"reporte_financiero.pdf\"");

        try (OutputStream os = response.getOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 36, 36, 80, 54);
            com.lowagie.text.pdf.PdfWriter writer = com.lowagie.text.pdf.PdfWriter.getInstance(document, os);

            // Añadir evento para header/footer (número de página y pie)
            writer.setPageEvent(new com.lowagie.text.pdf.PdfPageEventHelper() {
                com.lowagie.text.pdf.PdfTemplate total;
                com.lowagie.text.pdf.BaseFont bf;

                @Override
                public void onOpenDocument(com.lowagie.text.pdf.PdfWriter writer, Document document) {
                    try {
                        bf = com.lowagie.text.pdf.BaseFont.createFont(com.lowagie.text.pdf.BaseFont.HELVETICA, com.lowagie.text.pdf.BaseFont.WINANSI, com.lowagie.text.pdf.BaseFont.NOT_EMBEDDED);
                        total = writer.getDirectContent().createTemplate(50, 50);
                    } catch (Exception e) {
                        // ignore
                    }
                }

                @Override
                public void onEndPage(com.lowagie.text.pdf.PdfWriter writer, Document document) {
                    try {
                        com.lowagie.text.pdf.PdfContentByte cb = writer.getDirectContent();
                        // Encabezado: nombre de complejo a la izquierda, emitido por a la derecha
                        com.lowagie.text.Font headerFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 9, com.lowagie.text.Font.NORMAL, Color.DARK_GRAY);
                        String complejoHeader = "Complejo: " + (nombreComplejo == null ? "-" : nombreComplejo);
                        String emitidoHeader = "Emitido por: " + (generadoPor == null ? "-" : generadoPor);
                        com.lowagie.text.pdf.ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(complejoHeader, headerFont), document.left(), document.top() + 20, 0);
                        com.lowagie.text.pdf.ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, new Phrase(emitidoHeader, headerFont), document.right(), document.top() + 20, 0);

                        // Pie: "Página X de Y" usando plantilla para total de páginas
                        float x = document.right();
                        float y = document.bottom() - 10;
                        String text = "Página " + writer.getPageNumber() + " de ";

                        if (bf != null) {
                            cb.beginText();
                            cb.setFontAndSize(bf, 9);
                            // calcular anchura del texto para posicionarlo a la derecha
                            float textWidth = bf.getWidthPoint(text, 9);
                            cb.setTextMatrix(x - textWidth - 50, y);
                            cb.showText(text);
                            cb.endText();
                            // colocar plantilla justo después del texto
                            cb.addTemplate(total, x - 50, y);
                        } else {
                            com.lowagie.text.Font f = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 9, com.lowagie.text.Font.NORMAL, Color.DARK_GRAY);
                            com.lowagie.text.pdf.ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, new Phrase("Página " + writer.getPageNumber(), f), x, y, 0);
                        }
                    } catch (Exception ignored) {}
                }

                @Override
                public void onCloseDocument(com.lowagie.text.pdf.PdfWriter writer, Document document) {
                    try {
                        if (total != null && bf != null) {
                            total.beginText();
                            total.setFontAndSize(bf, 9);
                            String totalPages = String.valueOf(writer.getPageNumber() - 1);
                            total.showText(totalPages);
                            total.endText();
                        }
                    } catch (Exception ignored) {}
                }
            });
            document.open();

            // Encabezado del reporte (título, complejo, generado por, periodo, fecha)
            Font titleFont = new Font(com.lowagie.text.Font.HELVETICA, 16, com.lowagie.text.Font.BOLD);
            Paragraph title = new Paragraph("Reporte de Rendimiento Financiero", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            Font metaFont = new Font(com.lowagie.text.Font.HELVETICA, 10, com.lowagie.text.Font.NORMAL, Color.DARK_GRAY);
            String fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

            Paragraph info = new Paragraph();
            info.setAlignment(Element.ALIGN_CENTER);
            info.setSpacingAfter(8f);
            info.add(new Phrase("Complejo: " + (nombreComplejo == null || nombreComplejo.isEmpty() ? "Todos" : nombreComplejo) + "    ", metaFont));
            info.add(new Phrase("Emitido por: " + (generadoPor == null ? "-" : generadoPor) + "    ", metaFont));
            info.add(new Phrase("Periodo: " + (inicio != null ? inicio.toString() : "-") + " - " + (fin != null ? fin.toString() : "-"), metaFont));
            document.add(info);

            Paragraph fechaPar = new Paragraph("Generado: " + fecha, metaFont);
            fechaPar.setAlignment(Element.ALIGN_CENTER);
            fechaPar.setSpacingAfter(12f);
            document.add(fechaPar);

            // ══════════════════════════════════════════════════════════════════════════════
            // RESUMEN EJECUTIVO - MÉTRICAS CLAVE
            // ══════════════════════════════════════════════════════════════════════════════
            java.text.NumberFormat currency = java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-AR"));
            
            Font sectionFont = new Font(Font.HELVETICA, 13, Font.BOLD, new Color(37, 99, 235));
            Paragraph resumenTitle = new Paragraph("Resumen Ejecutivo", sectionFont);
            resumenTitle.setSpacingAfter(8f);
            document.add(resumenTitle);

            // Crear tabla de 4 columnas para las métricas (como tarjetas)
            PdfPTable kpiTable = new PdfPTable(4);
            kpiTable.setWidthPercentage(100f);
            kpiTable.setSpacingAfter(12f);

            Font kpiLabelFont = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY);
            Font kpiValueFont = new Font(Font.HELVETICA, 14, Font.BOLD, new Color(37, 99, 235));

            // KPI 1: Total Ingresos
            com.lowagie.text.pdf.PdfPCell cell1 = new com.lowagie.text.pdf.PdfPCell();
            cell1.setBorder(com.lowagie.text.Rectangle.BOX);
            cell1.setBorderColor(new Color(220, 220, 220));
            cell1.setPadding(8f);
            cell1.setBackgroundColor(new Color(248, 250, 252));
            Paragraph kpi1Label = new Paragraph("Total Ingresos", kpiLabelFont);
            kpi1Label.setAlignment(Element.ALIGN_CENTER);
            Paragraph kpi1Value = new Paragraph(currency.format(totalIngresos), kpiValueFont);
            kpi1Value.setAlignment(Element.ALIGN_CENTER);
            cell1.addElement(kpi1Label);
            cell1.addElement(kpi1Value);
            kpiTable.addCell(cell1);

            // KPI 2: Total Reservas
            com.lowagie.text.pdf.PdfPCell cell2 = new com.lowagie.text.pdf.PdfPCell();
            cell2.setBorder(com.lowagie.text.Rectangle.BOX);
            cell2.setBorderColor(new Color(220, 220, 220));
            cell2.setPadding(8f);
            cell2.setBackgroundColor(new Color(248, 250, 252));
            Paragraph kpi2Label = new Paragraph("Total Reservas", kpiLabelFont);
            kpi2Label.setAlignment(Element.ALIGN_CENTER);
            Paragraph kpi2Value = new Paragraph(String.valueOf(totalReservas), kpiValueFont);
            kpi2Value.setAlignment(Element.ALIGN_CENTER);
            cell2.addElement(kpi2Label);
            cell2.addElement(kpi2Value);
            kpiTable.addCell(cell2);

            // KPI 3: Promedio Diario
            com.lowagie.text.pdf.PdfPCell cell3 = new com.lowagie.text.pdf.PdfPCell();
            cell3.setBorder(com.lowagie.text.Rectangle.BOX);
            cell3.setBorderColor(new Color(220, 220, 220));
            cell3.setPadding(8f);
            cell3.setBackgroundColor(new Color(248, 250, 252));
            Paragraph kpi3Label = new Paragraph("Promedio Diario", kpiLabelFont);
            kpi3Label.setAlignment(Element.ALIGN_CENTER);
            Paragraph kpi3Value = new Paragraph(currency.format(promedioIngresoDiario), kpiValueFont);
            kpi3Value.setAlignment(Element.ALIGN_CENTER);
            cell3.addElement(kpi3Label);
            cell3.addElement(kpi3Value);
            kpiTable.addCell(cell3);

            // KPI 4: Ticket Promedio
            com.lowagie.text.pdf.PdfPCell cell4 = new com.lowagie.text.pdf.PdfPCell();
            cell4.setBorder(com.lowagie.text.Rectangle.BOX);
            cell4.setBorderColor(new Color(220, 220, 220));
            cell4.setPadding(8f);
            cell4.setBackgroundColor(new Color(248, 250, 252));
            Paragraph kpi4Label = new Paragraph("Ticket Promedio", kpiLabelFont);
            kpi4Label.setAlignment(Element.ALIGN_CENTER);
            Paragraph kpi4Value = new Paragraph(currency.format(ticketPromedio), kpiValueFont);
            kpi4Value.setAlignment(Element.ALIGN_CENTER);
            cell4.addElement(kpi4Label);
            cell4.addElement(kpi4Value);
            kpiTable.addCell(cell4);

            document.add(kpiTable);

            // Información adicional de cobertura
            Font infoFont = new Font(Font.HELVETICA, 9, Font.ITALIC, Color.DARK_GRAY);
            Paragraph infoCoverage = new Paragraph(
                String.format("Cobertura: %d días con datos de %d días totales en el periodo (%.1f%%)", 
                    diasConDatos, diasPeriodo, diasPeriodo > 0 ? (diasConDatos * 100.0 / diasPeriodo) : 0.0),
                infoFont
            );
            infoCoverage.setAlignment(Element.ALIGN_CENTER);
            infoCoverage.setSpacingAfter(12f);
            document.add(infoCoverage);

            // Gráfico: generar imagen del gráfico de líneas temporal y añadirla al documento
            try {
                DefaultCategoryDataset dataset = new DefaultCategoryDataset();
                for (ReporteFinancieroTemporalDTO r : datos) {
                    double val = r.getIngresos() == null ? 0.0 : r.getIngresos().doubleValue();
                    String label = r.getFecha() == null ? "-" : r.getFecha().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"));
                    dataset.addValue(val, "Ingresos Diarios", label);
                }

                JFreeChart chart = ChartFactory.createLineChart(
                    null,
                    "Fecha (día/mes)",
                    "Ingresos ($)",
                    dataset,
                    PlotOrientation.VERTICAL,
                    true,
                    true,
                    false
                );

                // Styling del chart para verse similar a la vista web
                CategoryPlot plot = chart.getCategoryPlot();
                chart.setBackgroundPaint(Color.WHITE);
                chart.setAntiAlias(true);
                chart.getRenderingHints().put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                plot.setBackgroundPaint(new Color(248,250,252)); // fondo claro similar al body
                plot.setRangeGridlinePaint(new Color(230,230,230));

                // Título opcional elegante (sin texto si no se quiere)
                chart.setTitle(new TextTitle("", new java.awt.Font("SansSerif", java.awt.Font.BOLD, 14)));

                // Colores para cada barra (paleta concordante con la vista web)
                final Paint[] PALETTE = new Paint[]{
                    new Color(37,99,235), // azul
                    new Color(239,68,68), // rojo
                    new Color(16,185,129), // verde
                    new Color(245,158,11), // naranja
                    new Color(14,165,233),
                    new Color(124,58,237),
                    new Color(255,107,107),
                    new Color(0,184,148),
                    new Color(255,159,67),
                    new Color(253,203,110)
                };

                BarRenderer renderer = new BarRenderer() {
                    @Override
                    public Paint getItemPaint(int row, int column) {
                    return PALETTE[column % PALETTE.length];
                    }
                };

                // Formato de las etiquetas dentro de las barras (moneda local)
                NumberFormat currencyFmt = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-AR"));
                renderer.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator("{2}", currencyFmt));
                renderer.setDefaultItemLabelsVisible(true);
                renderer.setDefaultItemLabelFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 11));
                renderer.setDefaultItemLabelPaint(Color.WHITE);
                // Posicionar la etiqueta centrada dentro de la barra (Página/PDF)
                try {
                    renderer.setDefaultPositiveItemLabelPosition(new ItemLabelPosition(ItemLabelAnchor.CENTER, TextAnchor.CENTER));
                } catch (NoClassDefFoundError | Exception ignore) {
                    // si la clase no está disponible en la versión de JFreeChart, ignorar
                }
                renderer.setMaximumBarWidth(0.12);
                renderer.setItemMargin(0.1);

                plot.setRenderer(renderer);

                // Ejes y fuentes
                CategoryAxis domainAxis = plot.getDomainAxis();
                domainAxis.setCategoryLabelPositions(CategoryLabelPositions.createUpRotationLabelPositions(Math.PI / 6.0));
                domainAxis.setTickLabelFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 10));

                plot.getRangeAxis().setTickLabelFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 10));

                int chartWidth = 1000;
                int chartHeight = 340;
                BufferedImage chartImage = chart.createBufferedImage(chartWidth, chartHeight);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(chartImage, "png", baos);
                baos.flush();
                byte[] chartBytes = baos.toByteArray();
                baos.close();

                com.lowagie.text.Image img = com.lowagie.text.Image.getInstance(chartBytes);
                img.scaleToFit(PageSize.A4.getHeight() - 72f, 250f);
                img.setAlignment(Element.ALIGN_CENTER);
                img.setSpacingAfter(12f);
                document.add(img);

                // Total general (ya calculado en el controlador, usar el parámetro)
                Paragraph totalPar = new Paragraph("Total ingresos en el periodo: " + currency.format(totalIngresos), 
                    new Font(com.lowagie.text.Font.HELVETICA, 12, com.lowagie.text.Font.BOLD));
                totalPar.setAlignment(Element.ALIGN_RIGHT);
                totalPar.setSpacingAfter(8f);
                document.add(totalPar);
            } catch (Exception chartEx) {
                logger.warn("No se pudo generar el gráfico en el PDF: {}", chartEx.getMessage());
            }

            // Tabla con columnas: Fecha, Cantidad Reservas, Ingresos
            PdfPTable table = new PdfPTable(3);
            table.setWidthPercentage(100f);
            table.setWidths(new float[]{3f, 2f, 2f});

            // Repetir cabecera en cada página
            table.setHeaderRows(1);

            Font headerFont = new Font(Font.HELVETICA, 11, Font.BOLD);
            table.addCell(new Phrase("Fecha", headerFont));
            table.addCell(new Phrase("Cantidad Reservas", headerFont));
            table.addCell(new Phrase("Ingresos", headerFont));

            Font cellFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
            // Reutilizar la instancia de currency ya definida arriba
            java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

            for (ReporteFinancieroTemporalDTO r : datos) {
                String fechaStr = r.getFecha() == null ? "-" : r.getFecha().format(dateFormatter);
                table.addCell(new Phrase(fechaStr, cellFont));
                table.addCell(new Phrase(String.valueOf(r.getCantidadReservas() == null ? 0L : r.getCantidadReservas()), cellFont));
                table.addCell(new Phrase(r.getIngresos() == null ? currency.format(0) : currency.format(r.getIngresos()), cellFont));
            }

            document.add(table);
            document.close();
            os.flush();
        } catch (Exception ex) {
            logger.error("Error generando PDF de reporte financiero: {}", ex.getMessage(), ex);
            // try to set error status if not committed
            try {
                response.reset();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } catch (Exception ignored) {}
        }
    }

    private String nullSafe(String s) { return s == null ? "-" : s; }

    public void generarReporteOcupacionPdf(java.util.List<Integer> horas,
                                           java.util.List<java.util.List<Long>> matrix,
                                           HttpServletResponse response,
                                           String generadoPor, String nombreComplejo,
                                           java.time.LocalDate inicio, java.time.LocalDate fin) {
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"reporte_ocupacion.pdf\"");

        try (OutputStream os = response.getOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 36, 36, 80, 54);
            com.lowagie.text.pdf.PdfWriter writer = com.lowagie.text.pdf.PdfWriter.getInstance(document, os);

            // Reutilizar evento simple para encabezado/pie (sin total de páginas complejo)
            writer.setPageEvent(new com.lowagie.text.pdf.PdfPageEventHelper() {
                @Override
                public void onEndPage(com.lowagie.text.pdf.PdfWriter writer, Document document) {
                    try {
                        com.lowagie.text.pdf.PdfContentByte cb = writer.getDirectContent();
                        com.lowagie.text.Font headerFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 9, com.lowagie.text.Font.NORMAL, java.awt.Color.DARK_GRAY);
                        String complejoHeader = "Complejo: " + (nombreComplejo == null ? "-" : nombreComplejo);
                        String emitidoHeader = "Emitido por: " + (generadoPor == null ? "-" : generadoPor);
                        com.lowagie.text.pdf.ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(complejoHeader, headerFont), document.left(), document.top() + 20, 0);
                        com.lowagie.text.pdf.ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, new Phrase(emitidoHeader, headerFont), document.right(), document.top() + 20, 0);
                    } catch (Exception ignored) {}
                }
            });

            document.open();

            Font titleFont = new Font(com.lowagie.text.Font.HELVETICA, 16, com.lowagie.text.Font.BOLD);
            Paragraph title = new Paragraph("Mapa de Calor - Ocupación Horaria", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            Font metaFont = new Font(com.lowagie.text.Font.HELVETICA, 10, com.lowagie.text.Font.NORMAL, java.awt.Color.DARK_GRAY);
            Paragraph info = new Paragraph();
            info.setAlignment(Element.ALIGN_CENTER);
            info.setSpacingAfter(8f);
            info.add(new Phrase("Complejo: " + (nombreComplejo == null || nombreComplejo.isEmpty() ? "Todos" : nombreComplejo) + "    ", metaFont));
            info.add(new Phrase("Emitido por: " + (generadoPor == null ? "-" : generadoPor) + "    ", metaFont));
            info.add(new Phrase("Periodo: " + (inicio != null ? inicio.toString() : "-") + " - " + (fin != null ? fin.toString() : "-"), metaFont));
            document.add(info);

            // Calcular máximo
            long max = 0L;
            if (matrix != null) {
                for (java.util.List<Long> row : matrix) for (Long v : row) if (v != null && v > max) max = v;
            }

            // Tabla: primera columna hora, luego Lunes..Domingo
            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100f);
            table.setWidths(new float[]{2f,1f,1f,1f,1f,1f,1f,1f});
            table.setHeaderRows(1);

            Font headerFont = new Font(Font.HELVETICA, 11, Font.BOLD);
            table.addCell(new Phrase("Hora", headerFont));
            String[] dias = new String[]{"Lunes","Martes","Miércoles","Jueves","Viernes","Sábado","Domingo"};
            for (String d : dias) table.addCell(new Phrase(d, headerFont));

            Font cellFont = new Font(Font.HELVETICA, 9, Font.NORMAL);

            // Orden de horas: la lista `horas` define el orden (ej 8..23,0)
            if (horas != null) {
                for (int i = 0; i < horas.size(); i++) {
                    Integer h = horas.get(i);
                    String horaLabel = String.format("%02d:00", h);
                    table.addCell(new Phrase(horaLabel, cellFont));

                    java.util.List<Long> row = (matrix != null && i < matrix.size()) ? matrix.get(i) : java.util.Collections.nCopies(7, 0L);
                    for (int d = 0; d < 7; d++) {
                        Long cnt = (row == null || d >= row.size() || row.get(d) == null) ? 0L : row.get(d);
                        com.lowagie.text.pdf.PdfPCell cell = new com.lowagie.text.pdf.PdfPCell(new Phrase(String.valueOf(cnt), cellFont));
                        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                        // Color según intensidad relativa (usar BaseColor para compatibilidad)
                        java.awt.Color bg;
                        if (cnt == 0) {
                            bg = new java.awt.Color(255,255,255);
                        } else {
                            double ratio = max == 0 ? 0.0 : ((double)cnt / (double)max);
                            if (ratio <= 0.25) bg = new java.awt.Color(255,230,230);
                            else if (ratio <= 0.6) bg = new java.awt.Color(255,160,160);
                            else bg = new java.awt.Color(204,0,0);
                        }
                        cell.setBackgroundColor(bg);
                        table.addCell(cell);
                    }
                }
            }

            document.add(table);
            document.close();
            os.flush();
        } catch (Exception ex) {
            logger.error("Error generando PDF de ocupación: {}", ex.getMessage(), ex);
            try { response.reset(); response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); } catch (Exception ignored) {}
        }
    }
}
