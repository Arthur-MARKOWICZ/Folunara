from __future__ import annotations

import io
import textwrap
import zipfile
from pathlib import Path
from xml.etree import ElementTree

from PIL import Image, ImageDraw, ImageFont
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle


ROOT = Path(__file__).resolve().parents[1]
TEST_DIR = ROOT / "output" / "emulator-tests"
PDF_DIR = ROOT / "output" / "pdf"


def generate_epub(path: Path) -> None:
    css = """
body { font-family: serif; line-height: 1.35; margin: 1em; }
h1 { color: #28334a; }
.callout { border-left: 0.35em solid #e29b32; padding: 0.8em; background: #f8f0df; }
""".strip()
    repeated = (
        "Este parágrafo foi criado para validar tamanho da fonte, altura de linha, margens e troca de tema. "
        "A leitura deve permanecer confortável e a posição não deve ser perdida ao alterar uma preferência. "
    )
    chapters = {
        "capitulo-1.xhtml": ("Capítulo 1 - Primeiros ajustes", repeated * 8),
        "capitulo-2.xhtml": ("Capítulo 2 - Paginação e rolagem", repeated * 12),
        "capitulo-3.xhtml": ("Capítulo 3 - Retomada da leitura", repeated * 10),
    }
    container = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>"""
    nav_items = "".join(
        f'<li><a href="{name}">{title}</a></li>' for name, (title, _) in chapters.items()
    )
    nav = f"""<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="pt-BR">
<head><title>Sumário</title></head><body><nav epub:type="toc"><h1>Sumário</h1><ol>{nav_items}</ol></nav></body></html>"""
    manifest = "".join(
        f'<item id="c{index}" href="{name}" media-type="application/xhtml+xml"/>'
        for index, name in enumerate(chapters, start=1)
    )
    spine = "".join(f'<itemref idref="c{index}"/>' for index in range(1, len(chapters) + 1))
    opf = f"""<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id" xml:lang="pt-BR">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="book-id">urn:uuid:ereader-test-epub</dc:identifier>
    <dc:title>Livro de teste do E-reader</dc:title>
    <dc:creator>Codex QA</dc:creator>
    <dc:language>pt-BR</dc:language>
    <meta property="dcterms:modified">2026-08-11T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="css" href="styles.css" media-type="text/css"/>{manifest}
  </manifest>
  <spine>{spine}</spine>
</package>"""
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("mimetype", "application/epub+zip", compress_type=zipfile.ZIP_STORED)
        archive.writestr("META-INF/container.xml", container)
        archive.writestr("OEBPS/content.opf", opf)
        archive.writestr("OEBPS/nav.xhtml", nav)
        archive.writestr("OEBPS/styles.css", css)
        for name, (title, body) in chapters.items():
            paragraphs = "".join(f"<p>{paragraph}</p>" for paragraph in textwrap.wrap(body, 500))
            xhtml = f"""<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" lang="pt-BR"><head><title>{title}</title>
<link rel="stylesheet" type="text/css" href="styles.css"/></head><body><h1>{title}</h1>
<p class="callout">Use o botão de configurações para personalizar este livro e depois restaurar os padrões globais.</p>
{paragraphs}</body></html>"""
            archive.writestr(f"OEBPS/{name}", xhtml)

    with zipfile.ZipFile(path) as archive:
        assert archive.namelist()[0] == "mimetype"
        assert archive.getinfo("mimetype").compress_type == zipfile.ZIP_STORED
        ElementTree.fromstring(archive.read("META-INF/container.xml"))
        ElementTree.fromstring(archive.read("OEBPS/content.opf"))
        for name in chapters:
            ElementTree.fromstring(archive.read(f"OEBPS/{name}"))


def generate_pdf(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    styles = getSampleStyleSheet()
    styles.add(ParagraphStyle(name="CenteredTitle", parent=styles["Title"], alignment=TA_CENTER, textColor=colors.HexColor("#28334A")))
    styles.add(ParagraphStyle(name="Small", parent=styles["BodyText"], fontSize=9, leading=12))
    story = [
        Paragraph("PDF de teste do E-reader", styles["CenteredTitle"]),
        Spacer(1, 8 * mm),
        Paragraph(
            "Este documento testa Original, Content Fit, ajuste à página, largura e altura. "
            "Ele contém texto corrido, áreas coloridas, tabela e diferentes margens.",
            styles["BodyText"],
        ),
        Spacer(1, 8 * mm),
        Table(
            [["Controle", "O que verificar"], ["Original", "Página inteira preservada"], ["Content Fit", "Redução segura das bordas"], ["Zoom", "Pinch e pan sem perder a página"]],
            colWidths=[42 * mm, 105 * mm],
            style=TableStyle([
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#28334A")),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#7A7F89")),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("PADDING", (0, 0), (-1, -1), 7),
            ]),
        ),
        Spacer(1, 90 * mm),
        Paragraph("Página 1 - visão geral", styles["Small"]),
    ]
    doc = SimpleDocTemplate(str(path), pagesize=A4, leftMargin=24 * mm, rightMargin=24 * mm, topMargin=22 * mm, bottomMargin=18 * mm)
    doc.build(story, onFirstPage=draw_pdf_page_one, onLaterPages=draw_pdf_later_pages)

    # Append specialized pages with reportlab canvas, then merge.
    from pypdf import PdfReader, PdfWriter
    from reportlab.pdfgen import canvas

    extra = io.BytesIO()
    c = canvas.Canvas(extra, pagesize=A4)
    width, height = A4
    c.setFont("Helvetica-Bold", 20)
    c.drawString(18 * mm, height - 22 * mm, "Página 2 - duas colunas")
    c.setFont("Helvetica", 10)
    column_text = "Coluna de teste com texto técnico, números 12345 e pontuação. " * 12
    for column, x in enumerate((18 * mm, 108 * mm)):
        text = c.beginText(x, height - 36 * mm)
        text.setLeading(13)
        for line in textwrap.wrap(column_text, 42):
            text.textLine(line)
        c.drawText(text)
        c.setFillColor(colors.HexColor("#E29B32" if column == 0 else "#526D82"))
        c.roundRect(x, 32 * mm, 76 * mm, 38 * mm, 4 * mm, fill=1, stroke=0)
    c.setFillColor(colors.black)
    c.drawCentredString(width / 2, 12 * mm, "Página 2 - teste de colunas e diagramas")
    c.showPage()

    c.setFont("Helvetica-Bold", 20)
    c.drawString(18 * mm, height - 22 * mm, "Página 3 - tabela")
    rows = [["Linha", "Valor A", "Valor B", "Estado"]] + [[str(i), str(i * 7), str(i * 13), "OK" if i % 2 else "Revisar"] for i in range(1, 16)]
    table = Table(rows, colWidths=[30 * mm, 38 * mm, 38 * mm, 45 * mm])
    table.setStyle(TableStyle([("GRID", (0, 0), (-1, -1), 0.5, colors.grey), ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#28334A")), ("TEXTCOLOR", (0, 0), (-1, 0), colors.white), ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F2F0EC")]), ("ALIGN", (1, 1), (-1, -1), "CENTER"), ("PADDING", (0, 0), (-1, -1), 6)]))
    table.wrapOn(c, width, height)
    table.drawOn(c, 22 * mm, height - 150 * mm)
    c.drawCentredString(width / 2, 12 * mm, "Página 3 - legibilidade de tabelas")
    c.showPage()

    c.setFont("Helvetica-Bold", 22)
    c.drawCentredString(width / 2, height / 2 + 18 * mm, "Página 4 - Content Fit")
    c.setFont("Helvetica", 12)
    c.drawCentredString(width / 2, height / 2 + 8 * mm, "Esta página possui margens grandes de propósito.")
    c.setStrokeColor(colors.HexColor("#E29B32"))
    c.setLineWidth(2)
    c.roundRect(52 * mm, height / 2 - 20 * mm, 106 * mm, 52 * mm, 4 * mm, stroke=1, fill=0)
    c.drawCentredString(width / 2, height / 2 - 10 * mm, "Ative Content Fit para aproximar este bloco.")
    c.showPage()
    c.save()
    extra.seek(0)

    writer = PdfWriter()
    for page in PdfReader(str(path)).pages:
        writer.add_page(page)
    for page in PdfReader(extra).pages:
        writer.add_page(page)
    with path.open("wb") as stream:
        writer.write(stream)


def draw_pdf_page_one(canvas, doc) -> None:
    canvas.saveState()
    canvas.setStrokeColor(colors.HexColor("#E29B32"))
    canvas.setLineWidth(2)
    canvas.rect(12 * mm, 12 * mm, A4[0] - 24 * mm, A4[1] - 24 * mm)
    canvas.restoreState()


def draw_pdf_later_pages(canvas, doc) -> None:
    canvas.saveState()
    canvas.setFont("Helvetica", 8)
    canvas.drawRightString(A4[0] - 15 * mm, 10 * mm, f"Página {doc.page}")
    canvas.restoreState()


def generate_cropbox_problem_pdf(path: Path) -> None:
    """Creates a valid PDF whose CropBox hides the end of a text line."""
    from pypdf import PdfReader, PdfWriter
    from reportlab.pdfgen import canvas

    path.parent.mkdir(parents=True, exist_ok=True)
    source = io.BytesIO()
    c = canvas.Canvas(source, pagesize=A4)
    width, height = A4
    c.setFont("Helvetica-Bold", 18)
    c.drawString(24 * mm, height - 25 * mm, "Fixture: texto além do CropBox")
    c.setFont("Helvetica", 12)
    c.drawString(24 * mm, height - 45 * mm, "Esta linha continua até a parte que será ocultada pelo CropBox: ABCDEFGHIJKLMNOPQRSTUVWXYZ")
    c.drawString(2 * mm, height / 2, "Texto fino junto à borda esquerda")
    c.drawRightString(width - 2 * mm, 3 * mm, "Texto fino junto às bordas direita e inferior")
    c.save()
    source.seek(0)

    page = PdfReader(source).pages[0]
    page.cropbox.lower_left = (0, 0)
    page.cropbox.upper_right = (width - 70 * mm, height)
    writer = PdfWriter()
    writer.add_page(page)
    with path.open("wb") as output:
        writer.write(output)


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    name = "arialbd.ttf" if bold else "arial.ttf"
    font_path = Path("C:/Windows/Fonts") / name
    return ImageFont.truetype(str(font_path), size) if font_path.exists() else ImageFont.load_default()


def generate_cbz(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    palette = ["#28334A", "#B85C38", "#3D6B5B", "#6B4E71", "#526D82", "#8C6A3F"]
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for page in range(1, 7):
            image = Image.new("RGB", (1200, 1800), palette[page - 1])
            draw = ImageDraw.Draw(image)
            title_font = load_font(116, bold=True)
            body_font = load_font(50)
            small_font = load_font(38)
            draw.rounded_rectangle((80, 80, 1120, 1720), radius=44, fill="#F7F0DF", outline="#E29B32", width=12)
            draw.text((600, 270), f"PÁGINA {page}", font=title_font, fill="#20242D", anchor="mm")
            draw.text((600, 470), "Arquivo CBZ de teste", font=body_font, fill="#28334A", anchor="mm")
            for index in range(3):
                top = 620 + index * 275
                draw.rounded_rectangle((160, top, 1040, top + 210), radius=28, fill=palette[(page + index) % len(palette)])
                draw.text((600, top + 105), f"QUADRO {index + 1}", font=body_font, fill="white", anchor="mm")
            draw.text((180, 1540), "← página anterior", font=small_font, fill="#20242D", anchor="lm")
            draw.text((1020, 1540), "próxima página →", font=small_font, fill="#20242D", anchor="rm")
            draw.text((600, 1640), "Teste LTR/RTL • Paginado/Vertical • Fit", font=small_font, fill="#20242D", anchor="mm")
            buffer = io.BytesIO()
            image.save(buffer, format="JPEG", quality=90, optimize=True)
            archive.writestr(f"pagina-{page:02d}.jpg", buffer.getvalue())

    with zipfile.ZipFile(path) as archive:
        assert len(archive.namelist()) == 6
        for name in archive.namelist():
            with Image.open(io.BytesIO(archive.read(name))) as page:
                assert page.size == (1200, 1800)


def main() -> None:
    epub = TEST_DIR / "teste-leitura.epub"
    pdf = PDF_DIR / "teste-documento.pdf"
    cbz = TEST_DIR / "teste-quadrinhos.cbz"
    cropbox_pdf = PDF_DIR / "fixture-cropbox-text.pdf"
    generate_epub(epub)
    generate_pdf(pdf)
    generate_cbz(cbz)
    generate_cropbox_problem_pdf(cropbox_pdf)
    for path in (epub, pdf, cbz, cropbox_pdf):
        print(f"{path}\t{path.stat().st_size} bytes")


if __name__ == "__main__":
    main()
