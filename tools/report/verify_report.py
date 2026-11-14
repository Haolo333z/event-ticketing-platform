"""Read-only checks on the generated Word/PDF and their evidence inputs."""
from pathlib import Path
import hashlib, json, re, shutil, sys
import pdfplumber
from pypdf import PdfReader
from docx import Document
from lxml import etree
from zipfile import ZipFile

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'docs/report'; QA=ROOT/'reports/t10-3'
render=Path(sys.argv[1]) if len(sys.argv)>1 else QA/'render-final'
pdf=render/'bao-cao-do-an.pdf';docx=OUT/'bao-cao-do-an.docx'
structure=json.loads((QA/'document-structure.json').read_text(encoding='utf-8'))
norm=lambda s: re.sub(r'\s+','',s).casefold()
reader=PdfReader(pdf);texts=[p.extract_text() or '' for p in reader.pages]
assert 30<=len(texts)<=50
assert len(texts)==43, f'Unexpected pagination {len(texts)}'
assert len(list(render.glob('page-*.png')))==len(texts)
assert all(len(t.strip())>150 for t in texts), 'Near-empty page'
assert not any(x in '\n'.join(texts) for x in ['Reference source not found','Error!','�','{{TOC_','{{LOAD_'])
assert all(re.search(r'\b'+str(i+1)+r'\s*$',t) for i,t in enumerate(texts) if i>0), 'Footer page mismatch'
heading_pages={};toc_checks=[]
for entry in structure['tocEntries']:
    title=entry['title'];actual=[i+1 for i,t in enumerate(texts) if i>=4 and norm(title) in norm(t)]
    assert entry['page'] in actual, f'Wrong TOC: {title}: {entry["page"]} != {actual}'
    heading_pages[title]=entry['page'];toc_checks.append(dict(title=title,page=entry['page'],matches=True))
    # The static title and live PAGEREF must both occur on a TOC page.
    pat=re.escape(norm(title))+r'\.*'+str(entry['page'])
    assert any(re.search(pat,norm(t)) for t in texts[2:4]), f'TOC text missing {title}'
(QA/'heading-pages.json').write_text(json.dumps(heading_pages,ensure_ascii=False,indent=2),encoding='utf-8')

doc=Document(docx);assert len(doc.tables)==24 and len(doc.inline_shapes)==8
assert len(doc.paragraphs)>200
ns={'w':'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
with ZipFile(docx) as z:
    xml=etree.fromstring(z.read('word/document.xml'));styles=etree.fromstring(z.read('word/styles.xml'));settings=etree.fromstring(z.read('word/settings.xml'))
    assert not settings.xpath('//w:documentProtection',namespaces=ns)
    assert not xml.xpath('//w:pBdr',namespaces=ns) and not styles.xpath('//w:pBdr',namespaces=ns)
    bookmarks=set(xml.xpath('//w:bookmarkStart/@w:name',namespaces=ns))
    refs=[x.text.strip().split()[1] for x in xml.xpath('//w:instrText',namespaces=ns) if (x.text or '').strip().startswith('PAGEREF')]
    assert len(refs)==len(structure['tocEntries']) and all(r in bookmarks for r in refs)
    assert len(xml.xpath('//w:tblHeader',namespaces=ns))==24
    assert len(structure['placeholders'])==7
    assert not xml.xpath('//w:rFonts/@w:asciiTheme',namespaces=ns)
body_bounds=[];fonts=set()
with pdfplumber.open(pdf) as pd:
    for pn,page in enumerate(pd.pages,1):
        chars=[c for c in page.chars if c.get('text','').strip()]
        for c in chars:fonts.add(c['fontname'])
        # All content, including header/footer, must remain inside the page.
        bad=[c for c in chars if c['x0']<65 or c['x1']>page.width-40 or c['top']<15 or c['bottom']>page.height-18]
        assert not bad, f'Outside intended page area on {pn}'
        body=[c for c in chars if 58<c['top']<page.height-55]
        body_bounds.append({'page':pn,'characters':len(chars),'minX':round(min(c['x0'] for c in chars),2),
                            'maxX':round(max(c['x1'] for c in chars),2),'bodyBottom':round(max(c['bottom'] for c in body),2) if body else None,
                            'images':len(page.images)})
assert any('TimesNewRoman' in f or 'Times' in f for f in fonts)
assert not any('Calibri' in f or 'Carlito' in f for f in fonts), 'Unexpected theme font'

snapshots=json.loads((ROOT/'docs/evidence/t9-3-snapshots-2026-11-14.json').read_text(encoding='utf-8-sig'))
assert len(snapshots)==37
counter_count=0
for row in snapshots:
    # Snapshot collector keeps the computed checks alongside the raw rows.
    v=row.get('violations') or row['snapshot'].get('violations')
    assert isinstance(v,dict), f'Unknown snapshot format {row.keys()}'
    assert len(v)==11 and all(x==0 for x in v.values())
    counter_count+=len(v)
assert counter_count==407

claims=json.loads((QA/'claims-check.json').read_text())
load_page=texts[heading_pages['7.2 Số liệu tải theo từng lượt']-1]
load=json.loads((ROOT/'docs/evidence/t9-2-load-2026-11-14.json').read_text(encoding='utf-8-sig'))
for row in load['measurements']:
    for key in ['p50Ms','p95Ms','p99Ms']:
        assert f'{row["summary"]["latency"][key]:.3f}'.replace('.',',') in load_page
        
shutil.copy2(pdf,OUT/'bao-cao-do-an.pdf')
(QA/'pdf-text.txt').write_text('\n\n'.join(f'--- PAGE {i+1} ---\n{t}' for i,t in enumerate(texts)),encoding='utf-8')
result={'status':'PASS_AUTOMATED','pages':len(texts),'renderedPngs':len(texts),'tocEntries':len(toc_checks),
        'tocChecks':toc_checks,'tables':len(doc.tables),'figures':len(doc.inline_shapes),'editableParagraphs':len(doc.paragraphs),
        'placeholders':structure['placeholders'],'fonts':sorted(fonts),'pageBounds':body_bounds,
        'recoveryCountersChecked':counter_count,'sourceClaims':claims,
        'finalArtifacts':[{'path':str(p.relative_to(ROOT)),'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}
                          for p in [docx,OUT/'bao-cao-do-an.pdf',OUT/'BAO_CAO.md']],
        'visualReview':'PENDING explicit inspection of every final page'}
(QA/'automated-qa.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(f'PASS: {len(texts)} pages, {len(toc_checks)} TOC entries,24 tables,8 figures,407 recovery counters and load CSV checks')
