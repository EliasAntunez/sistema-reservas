package com.example.tureserva.servicio;

import com.example.tureserva.servicio.dto.ReporteFinancieroDTO;
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

    public void generarReporteFinancieroPdf(List<ReporteFinancieroDTO> datos, HttpServletResponse response,
                                            String generadoPor, String nombreComplejo,
                                            java.time.LocalDate inicio, java.time.LocalDate fin) {
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

            // Gráfico: generar imagen del gráfico de barras y añadirla al documento
            try {
                DefaultCategoryDataset dataset = new DefaultCategoryDataset();
                for (ReporteFinancieroDTO r : datos) {
                    double val = r.getIngresos() == null ? 0.0 : r.getIngresos().doubleValue();
                    String label = r.getEspacioNombre() == null ? "-" : r.getEspacioNombre();
                    dataset.addValue(val, "Ingresos", label);
                }

                JFreeChart chart = ChartFactory.createBarChart(
                    null,
                    "",
                    "",
                    dataset,
                    PlotOrientation.VERTICAL,
                    false,
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

                // Total general
                BigDecimal total = datos.stream()
                    .map(r -> r.getIngresos() == null ? BigDecimal.ZERO : r.getIngresos())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                java.text.NumberFormat currency = java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-AR"));
                Paragraph totalPar = new Paragraph("Total ingresos: " + currency.format(total), new Font(com.lowagie.text.Font.HELVETICA, 12, com.lowagie.text.Font.BOLD));
                totalPar.setAlignment(Element.ALIGN_RIGHT);
                totalPar.setSpacingAfter(8f);
                document.add(totalPar);
            } catch (Exception chartEx) {
                logger.warn("No se pudo generar el gráfico en el PDF: {}", chartEx.getMessage());
            }

            // Tabla con columnas: Espacio, Tipo, Cantidad Reservas, Ingresos
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100f);
            table.setWidths(new float[]{4f, 2f, 2f, 2f});

            // Repetir cabecera en cada página
            table.setHeaderRows(1);

            Font headerFont = new Font(Font.HELVETICA, 11, Font.BOLD);
            table.addCell(new Phrase("Espacio", headerFont));
            table.addCell(new Phrase("Tipo", headerFont));
            table.addCell(new Phrase("Cantidad Reservas", headerFont));
            table.addCell(new Phrase("Ingresos", headerFont));

            Font cellFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
            java.text.NumberFormat currency = java.text.NumberFormat.getCurrencyInstance();

            for (ReporteFinancieroDTO r : datos) {
                table.addCell(new Phrase(nullSafe(r.getEspacioNombre()), cellFont));
                table.addCell(new Phrase(nullSafe(r.getTipoEspacio()), cellFont));
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
}
