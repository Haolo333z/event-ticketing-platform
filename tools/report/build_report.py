"""Build the T10.3 editable report from prose and repository evidence.

Use the Codex primary runtime Python (python-docx and Pillow). Never reads .env
or writes to a product database. Figure contents are explanatory source diagrams.
"""
from pathlib import Path
import argparse, csv, hashlib, json, math, re, textwrap
from collections import Counter
from docx import Document
from docx.shared import Cm, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK, WD_TAB_ALIGNMENT, WD_TAB_LEADER
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'docs/report'
QA = ROOT / 'reports/t10-3'
EVID = ROOT / 'docs/evidence'
FONT = Path('C:/Windows/Fonts/times.ttf')
BLACK = '000000'

def read(name):
    return json.loads((EVID/name).read_text(encoding='utf-8-sig'))

def num(x, decimals=3):
    return f'{x:.{decimals}f}'.replace('.', ',')

def md_table(headers, rows):
    return '\n'.join(['| '+' | '.join(headers)+' |', '| '+' | '.join(['---']*len(headers))+' |'] +
                     ['| '+' | '.join(str(v) for v in row)+' |' for row in rows])

def evidence_macros():
    e1, e2, e3, e6 = [read(n+'-2026-11-14.json') for n in
                       ['t9-1-invariants', 't9-2-load', 't9-3-recovery', 't10-2-rehearsal']]
    assert e1['results']['backend'] == dict(tests=130, failures=0, errors=0, skipped=0)
    assert e3['backend'] == dict(tests=130, failures=0, errors=0, skipped=0)
    assert e6['checks']['backend']['passed'] == 9 and e6['checks']['backend']['skipped'] == 121
    e4=read('t9-4-audit-2026-11-14.json');ci=read('t9-4-ci39-2026-11-14.json')
    assert e4['results']['backend']['tests']==130 and e4['results']['backend']['skipped']==0
    assert ci['sha']=='984e95d7dc7aca0b7ea94743e27e21d5d713d906'
    assert ci['run']==39 and ci['conclusion']=='success' and len(ci['jobs'])==4
    assert all(j['conclusion']=='success' and j['sha']==ci['sha'] for j in ci['jobs'])
    samples = list(csv.DictReader((EVID/'t9-2-samples-2026-11-14.csv').open(encoding='utf-8-sig')))
    measured = [s for s in samples if s['warmup'].lower() == 'false']
    assert len(measured) == 300
    claims = []
    rows = []
    for ix, m in enumerate(e2['measurements']):
        s = m['summary']; selected = [r for r in measured if r['round'] == m['name']]
        assert len(selected) == 50 and m['concurrency']['peakInFlight'] == 50
        assert s['otherErrors'] == 0 and m['integrity']['violationCountersSum'] == 0
        vals = sorted(float(r['latencyMs']) for r in selected)
        for p, key in [(0.5, 'p50Ms'), (.95,'p95Ms'), (.99,'p99Ms')]:
            assert abs(vals[math.ceil(p*len(vals))-1]-s['latency'][key]) < 1e-6
        assert sum(r['outcome']=='success' for r in selected) == s['success']
        assert sum(r['outcome']=='expectedConflict' for r in selected) == s['expectedConflict']
        assert abs(50/(s['elapsedMs']/1000)-s['completedRequestsPerSecond']) < 1e-7
        rows.append([f"{m['round']} {m['mode']}", s['success'],s['expectedConflict'],s['otherErrors'],
                     num(s['latency']['p50Ms']),num(s['latency']['p95Ms']),num(s['latency']['p99Ms']),num(s['completedRequestsPerSecond'])])
        claims.append({'source':'t9-2-load-2026-11-14.json','pointer':f'measurements[{ix}]',
                       'csvRows':len(selected),'percentilesAndThroughputIndependentlyMatched':True,
                       'success':s['success'],'conflicts':s['expectedConflict'],'otherErrors':s['otherErrors']})
    load = md_table(['Lượt mode','202','409','Khác','p50 ms','p95 ms','p99 ms','Req/s'], rows)
    aggregate_rows=[]
    for mode in ['GA','SEATED']:
        a=e2['aggregates'][mode]; vals=sorted(float(r['latencyMs']) for r in measured if r['mode']==mode)
        assert len(vals)==150
        for p,key in [(.5,'p50Ms'),(.95,'p95Ms'),(.99,'p99Ms')]:
            assert abs(vals[math.ceil(p*len(vals))-1]-a['latency'][key]) < 1e-6
        aggregate_rows.append([mode,a['requests'],a['success'],a['expectedConflict'],a['otherErrors'],
                               num(a['latency']['p50Ms']),num(a['latency']['p95Ms']),num(a['latency']['p99Ms'])])
    agg=md_table(['Mode','Mẫu','202','409','Khác','p50 ms','p95 ms','p99 ms'],aggregate_rows)
    alloc=[]
    for mode in ['GA','SEATED']:
        m=next(m for m in e2['measurements'] if m['mode']==mode)
        for stage in ['afterHold','afterSale']:
            a=m[stage]
            alloc.append([mode, 'Sau giữ' if stage=='afterHold' else 'Sau bán',a['capacity'],a['held'],a['sold'],a['bookings'],a['tickets'],a['oversell']])
    allocation=md_table(['Mode','Mốc','Cap','Held','Sold','Booking','Vé','Oversell'],alloc)
    suite=md_table(['Gate hoặc lượt','Backend thực chạy','Skip','Frontend','Contract'],[
        ['T9.1',e1['results']['backend']['tests'],0,54,69],['T9.3',e3['backend']['tests'],0,54,69],
        ['T9.4 local',e4['results']['backend']['tests'],0,e4['results']['frontend']['tests'],e4['results']['contracts']['tests']],['T10.2 launcher',e6['checks']['backend']['passed'],e6['checks']['backend']['skipped'],e6['checks']['frontend']['passed'],e6['checks']['contracts']['passed']]])
    proof=e3['comparisonProofs']
    def criterion(start): return next(p['measured'] for p in proof if p['criterion'].startswith(start))
    duplicate=criterion('MSG-01 consumer restart'); fail=criterion('Consumer3failed'); poison=criterion('Poison retained'); replay=criterion('Payment/BookingConfirmed')
    recovery=md_table(['Ca T9.3','Trước hoặc trong lỗi','Sau phục hồi'],[
        ['Consumer restart/duplicate',f"{duplicate['ticketsBefore']} vé",f"{duplicate['ticketsAfter']} vé; historyCONFIRMED={duplicate['confirmedHistory']}"],
        ['Ba delivery lỗi',f"Inbox{fail['failedInbox']}, vé{fail['failedTickets']}",f"Inbox{fail['recoveredInbox']}, vé{fail['recoveredTickets']}, RESOLVED{fail['resolvedFailures']}"],
        ['Poison hai consumer',f"Mỗi consumer{poison['attemptsPerConsumer']}attempt",f"{poison['newFailureRows']}failure và{poison['dlqPublished']}DLQ"],
        ['Replay payment/confirmed',f"{replay['tickets']}vé/{replay['historyRows']}history",'Hash trước=sau; không thêm nghiệp vụ']])
    timing=e6['main']['timing']
    demo=md_table(['Hạng mục T10.2','Kết quả ghi nhận'],[
        ['Lượt kỹ thuật',f"8/8 bước; {num(e6['repeatability']['firstTiming']['totalSeconds'])} giây"],
        ['Lượt có nhịp trình diễn',f"8/8 bước; {num(timing['totalSeconds'])} giây"],
        ['Khoảng xem/thuyết minh',f"{timing['reviewSeconds']} giây được đặt"],
        ['Lệnh và overhead còn lại',f"{num(timing['activeSeconds'])} giây"],
        ['Stop/Start','Exit0, healthUP;18hashbảng/9migration giữ nguyên'],
        ['Chrome trong T10.2','Chưa thao tác hoặc quan sát bởi agent']])
    assert e3['snapshots']['count']==37 and e3['snapshots']['violationCountersPerSnapshot']==11
    assert all(v==0 for v in e6['main']['final']['violations'].values())
    macros=dict(LOAD_ROUNDS=load,LOAD_AGGREGATE=agg,ALLOCATION_TABLE=allocation,SUITE_TABLE=suite,RECOVERY_TABLE=recovery,DEMO_TABLE=demo)
    QA.mkdir(parents=True,exist_ok=True)
    (QA/'claims-check.json').write_text(json.dumps({'status':'PASS','csvMeasuredRows':len(measured),
        'validatedRounds':claims,'backendGateTests':130,'launcherExecuted':9,'launcherSkipped':121,
        'recoverySnapshots':37,'countersPerSnapshot':11,'demoSeconds':timing['totalSeconds']},ensure_ascii=False,indent=2),encoding='utf-8')
    return macros

def fig(name, nodes, edges, size=(1800,900)):
    im=Image.new('RGB',size,'white');d=ImageDraw.Draw(im)
    font=ImageFont.truetype(str(FONT),43); small=ImageFont.truetype(str(FONT),36)
    def label(text, xy, width, f=font):
        lines=[]
        for para in text.split('\n'):
            words=para.split(); line=''
            for w in words:
                trial=(line+' '+w).strip()
                if d.textlength(trial,font=f)>width and line:lines.append(line);line=w
                else:line=trial
            lines.append(line)
        h=len(lines)*51;y=xy[1]-h/2
        for line in lines:
            d.text((xy[0],y),line,fill='black',font=f,anchor='mt');y+=51
    # Edges first, then opaque nodes keep arrow paths from crossing node text.
    for points, text, tx in edges:
        d.line(points,fill='#344050',width=5,joint='curve')
        x,y=points[-1];px,py=points[-2];a=math.atan2(y-py,x-px)
        tip=[(x,y),(x-22*math.cos(a-.45),y-22*math.sin(a-.45)),(x-22*math.cos(a+.45),y-22*math.sin(a+.45))]
        d.polygon(tip,fill='#344050')
        if text:
            bb=d.textbbox((0,0),text,font=small);w=bb[2]-bb[0]
            d.rectangle((tx[0]-w/2-7,tx[1]-27,tx[0]+w/2+7,tx[1]+27),fill='white')
            d.text(tx,text,font=small,fill='black',anchor='mm')
    for rect, text in nodes:
        d.rounded_rectangle(rect,radius=15,fill='#eff3f7',outline='#4c5968',width=4)
        label(text,((rect[0]+rect[2])/2,(rect[1]+rect[3])/2),rect[2]-rect[0]-35)
    (OUT/'figures').mkdir(exist_ok=True)
    im.save(OUT/'figures'/f'{name}.png',dpi=(300,300))

def figures():
    fig('architecture',[
      ((570,15,1230,155),'React và TypeScript\nCustomer hoặc ADMIN'),
      ((60,300,790,490),'HTTP API Spring Boot\nDomain và transaction'),
      ((1010,300,1740,490),'Worker trong backend\nExpiry, publisher, consumer'),
      ((60,720,790,875),'PostgreSQL\nNghiệp vụ và outbox/inbox'),
      ((1010,720,1740,875),'Kafka một node\nBooking topic và DLQ')],
      [([(700,155),(425,300)],'HTTP',(435,205)), ([(790,390),(1010,390)],'Trong monolith',(900,260)),
       ([(425,490),(425,720)],'Transaction',(425,610)), ([(1120,490),(700,720)],'Ledger bền',(880,630)),
       ([(1320,490),(1320,720)],'Gửi',(1260,610)), ([(1600,720),(1600,490)],'Nhận',(1665,610))])
    fig('transaction',[
      ((35,60,560,220),'Principal và validation'),((640,60,1160,220),'Reserve key\nTrong transaction'),((1240,60,1765,220),'Khóa event và type\nGiá do server'),
      ((1240,360,1765,540),'Booking và giữ tài nguyên'),((640,360,1160,540),'Item, history, outbox\nResponse của key'),((35,360,560,540),'COMMIT\nSau đó HTTP 202'),
      ((420,730,1380,875),'Bất kỳ lỗi ghi nào: ROLLBACK toàn bộ')],
      [([(560,140),(640,140)],'',(0,0)), ([(1160,140),(1240,140)],'',(0,0)), ([(1500,220),(1500,360)],'',(0,0)),
       ([(1240,450),(1160,450)],'',(0,0)), ([(640,450),(560,450)],'',(0,0)), ([(900,540),(900,730)],'Lỗi',(950,635))])
    fig('eda',[
      ((30,40,550,200),'TX nghiệp vụ\nBooking và outbox'),((640,40,1160,200),'COMMIT PostgreSQL'),((1250,40,1770,200),'Publisher gửi\nKafka ACK'),
      ((1250,380,1770,550),'Đánh dấu PUBLISHED\nCó thể crash trước bước này'),((640,380,1160,550),'Consumer nhận\nInbox và hiệu ứng cùng TX'),((30,380,550,550),'COMMIT PostgreSQL\nSau đó ACK offset'),
      ((250,735,1550,875),'Gửi hoặc nhận lặp cùng eventId: inbox bảo vệ no-op')],
      [([(550,120),(640,120)],'',(0,0)), ([(1160,120),(1250,120)],'',(0,0)), ([(1510,200),(1510,380)],'',(0,0)),
       ([(1250,160),(1195,160),(1195,300),(900,300),(900,380)],'Delivery',(970,280)), ([(640,465),(550,465)],'',(0,0)), ([(290,550),(290,700),(900,700),(900,735)],'',(0,0))])
    fig('er',[
      ((30,20,540,180),'users\nPK id'),((645,20,1155,180),'events → ticket_types\nPK và FK event'),((1260,20,1770,180),'inventory_ga\nPK ticket_type_id'),
      ((30,350,540,515),'bookings\naccount, event, type'),((645,350,1155,515),'booking_items\nBooking và giá snapshot'),((1260,350,1770,515),'seats\nOwner hiện tại, trạng thái'),
      ((30,710,540,875),'payments\nKết quả có recorded_at'),((645,710,1155,875),'tickets\nItem và ordinal unique'),((1260,710,1770,875),'payment_compensations\nUnique payment_id')],
      [([(285,180),(285,350)],'1:N',(290,265)), ([(900,180),(900,260),(420,260),(420,350)],'Event/type',(630,240)),
       ([(1155,100),(1260,100)],'1:0..1',(1210,230)), ([(540,430),(645,430)],'1:N',(590,330)),
       ([(1155,430),(1260,430)],'Seat lịch sử',(1200,570)), ([(285,515),(285,710)],'1:N',(285,610)),
       ([(900,515),(900,710)],'1:N',(900,610)), ([(540,795),(590,795),(590,675),(1515,675),(1515,710)],'Bù trừ',(1240,660))])
    fig('states',[
      ((540,30,1260,195),'PENDING_PAYMENT'),((35,365,540,535),'CONFIRMED'),((650,365,1150,535),'PAYMENT_FAILED'),((1260,365,1765,535),'CANCELLED'),
      ((270,725,790,875),'EXPIRED'),((1040,725,1710,875),'REFUND_REQUIRED')],
      [([(680,195),(290,365)],'Success đúng hạn',(325,260)), ([(900,195),(900,365)],'Failure',(940,270)),
       ([(1130,195),(1510,365)],'Hủy',(1490,260)), ([(540,150),(12,150),(12,640),(530,640),(530,725)],'Hết hạn',(240,625)),
       ([(790,800),(1040,800)],'Late success',(920,700))])
    fig('seat-race',[
      ((50,40,790,220),'Request A và request B\nCùng chọn ghế A1'),((1010,40,1750,220),'PostgreSQL\nA1 đang AVAILABLE'),
      ((50,365,790,550),'A lấy khóa, kiểm lại và giữ\nBooking/key/history/outbox'),((1010,365,1750,550),'B đợi khóa rồi kiểm lại\nA1 đã HELD'),
      ((50,730,790,875),'A COMMIT → HTTP 202'),((1010,730,1750,875),'B ROLLBACK → HTTP 409')],
      [([(790,130),(1010,130)],'FOR UPDATE',(900,270)), ([(1380,220),(1380,365)],'',(0,0)),
       ([(420,220),(420,365)],'',(0,0)), ([(420,550),(420,730)],'',(0,0)), ([(1380,550),(1380,730)],'',(0,0)),
       ([(790,450),(1010,450)],'Sau A commit',(900,620))])
    fig('payment',[
      ((510,25,1290,185),'Ghi result và recorded_at\nCOMMIT ledger cùng outbox'),
      ((50,350,805,540),'Còn PENDING_PAYMENT\nCó timely success và hold hợp lệ'),((995,350,1750,540),'EXPIRED đã commit\nHoặc result sau deadline'),
      ((50,730,805,875),'CONFIRMED và phát đúng vé\nHeld chuyển sang sold'),((995,730,1750,875),'REFUND_REQUIRED\nKhông vé, không nhận lại hold')],
      [([(700,185),(425,350)],'DB clock',(390,270)), ([(1100,185),(1375,350)],'State guard',(1410,270)),
       ([(425,540),(425,730)],'Cùng transaction',(425,635)), ([(1375,540),(1375,730)],'Một nghĩa vụ bù trừ',(1375,635))])
    fig('recovery',[
      ((35,35,550,215),'Consumer xử lý lỗi\nTổng 3 delivery'),((640,35,1160,215),'Failure DB COMMIT\nRecord và vị trí Kafka'),((1250,35,1765,215),'Sau đó ACK offset'),
      ((1250,360,1765,550),'Worker gửi DLQ\nSau commit, hữu hạn'),((640,360,1160,550),'ADMIN điều tra nguyên nhân\nReplay original event'),((35,360,550,550),'Inbox và state guard\nApply hoặc no-op'),
      ((420,745,1380,875),'Không xóa inbox, không force, audit và budget giữ nguyên')],
      [([(550,125),(640,125)],'',(0,0)), ([(1160,125),(1250,125)],'',(0,0)), ([(1510,215),(1510,360)],'',(0,0)),
       ([(900,215),(900,360)],'',(0,0)), ([(640,455),(550,455)],'',(0,0)), ([(290,550),(290,680),(900,680),(900,745)],'',(0,0))])

def set_font(style, size, bold=False):
    style.font.name='Times New Roman';style.font.size=Pt(size);style.font.bold=bold;style.font.color.rgb=RGBColor(0,0,0)
    rp=style.element.get_or_add_rPr();rf=rp.find(qn('w:rFonts'))
    if rf is None:rf=OxmlElement('w:rFonts');rp.append(rf)
    for attr in ['ascii','hAnsi','eastAsia','cs']:rf.set(qn('w:'+attr),'Times New Roman')
    lang=OxmlElement('w:lang');lang.set(qn('w:val'),'vi-VN');rp.append(lang)
    for attr in ['asciiTheme','hAnsiTheme','eastAsiaTheme','cstheme','csTheme']:
        rf.attrib.pop(qn('w:'+attr),None)

def field(p, instruction, cached):
    r=OxmlElement('w:r');a=OxmlElement('w:fldChar');a.set(qn('w:fldCharType'),'begin');r.append(a);p._p.append(r)
    r=OxmlElement('w:r');t=OxmlElement('w:instrText');t.set(qn('xml:space'),'preserve');t.text=' '+instruction+' ';r.append(t);p._p.append(r)
    r=OxmlElement('w:r');a=OxmlElement('w:fldChar');a.set(qn('w:fldCharType'),'separate');r.append(a);p._p.append(r)
    p.add_run(str(cached))
    r=OxmlElement('w:r');a=OxmlElement('w:fldChar');a.set(qn('w:fldCharType'),'end');r.append(a);p._p.append(r)

def bookmark(p,name,ix):
    a=OxmlElement('w:bookmarkStart');a.set(qn('w:id'),str(ix));a.set(qn('w:name'),name)
    z=OxmlElement('w:bookmarkEnd');z.set(qn('w:id'),str(ix));p._p.insert(0,a);p._p.append(z)

def add_table(doc, lines):
    rows=[[c.strip().replace('`','') for c in l.strip().strip('|').split('|')] for l in lines]
    rows=[r for r in rows if not all(re.fullmatch('[-: ]+',c) for c in r)]
    n=len(rows[0]); table=doc.add_table(rows=0,cols=n);table.alignment=WD_TABLE_ALIGNMENT.CENTER;table.autofit=False
    widths={2:[4.6,11.4],3:[4.3,5.85,5.85],4:[3.9,4.0,4.0,4.1],5:[4.2,3.6,2.0,3.1,3.1],8:[2.4,1.1,1.1,1.1,2.1,2.1,2.1,4.0]}.get(n,[16/n]*n)
    if n==8:widths=[2.3,1.1,1.1,1.1,2.0,2.0,2.0,4.4] if 'Req/s' not in rows[0] and 'p99 ms' not in rows[0] else [2.5,1.0,1.0,1.0,2.2,2.2,2.2,1.7]
    total=sum(widths);widths=[w*16/total for w in widths]
    for col,w in zip(table.columns,widths):col.width=Cm(w)
    props=table._tbl.tblPr;borders=OxmlElement('w:tblBorders')
    for side in ['top','left','bottom','right','insideH','insideV']:
        e=OxmlElement('w:'+side);e.set(qn('w:val'),'single');e.set(qn('w:sz'),'5');e.set(qn('w:color'),'D9D9D9');borders.append(e)
    props.append(borders)
    for ix,row in enumerate(rows):
        cells=table.add_row().cells
        trpr=table.rows[-1]._tr.get_or_add_trPr();nosplit=OxmlElement('w:cantSplit');trpr.append(nosplit)
        if ix==0:repeat=OxmlElement('w:tblHeader');trpr.append(repeat)
        for j,text in enumerate(row):
            c=cells[j];c.width=Cm(widths[j]);c.vertical_alignment=WD_CELL_VERTICAL_ALIGNMENT.CENTER
            tcpr=c._tc.get_or_add_tcPr();marg=OxmlElement('w:tcMar')
            for side in ['top','bottom','left','right']:
                e=OxmlElement('w:'+side);e.set(qn('w:w'),'85');e.set(qn('w:type'),'dxa');marg.append(e)
            tcpr.append(marg)
            if ix==0:shade=OxmlElement('w:shd');shade.set(qn('w:fill'),'DFE8F0');tcpr.append(shade)
            p=c.paragraphs[0];p.paragraph_format.space_after=Pt(0);p.paragraph_format.space_before=Pt(0);p.paragraph_format.first_line_indent=Cm(0);p.paragraph_format.line_spacing=1.06
            p.alignment=WD_ALIGN_PARAGRAPH.CENTER if (n>=5 or ix==0) else WD_ALIGN_PARAGRAPH.LEFT
            r=p.add_run(text);r.font.size=Pt(10.5 if n>=5 else 11);r.bold=ix==0;r.font.color.rgb=RGBColor(0,0,0)
    doc.add_paragraph().paragraph_format.space_after=Pt(0)

def main():
    OUT.mkdir(parents=True,exist_ok=True);QA.mkdir(parents=True,exist_ok=True)
    figures();macros=evidence_macros()
    text=(ROOT/'tools/report/content.md').read_text(encoding='utf-8')
    for key,value in macros.items():text=text.replace('{{'+key+'}}',value)
    pages=re.split(r'<!-- PAGE ([a-z0-9]+) -->',text)[1:]
    parts=list(zip(pages[::2],pages[1::2]));assert len(parts)==43
    toc=[];heading_ids={};counter=1
    page_map=json.loads((QA/'heading-pages.json').read_text()) if (QA/'heading-pages.json').exists() else {}
    for page_no,(slug,part) in enumerate(parts,1):
        if slug in ['cover','toc1','toc2','abstract','glossary']:continue
        # PowerShell comments inside fenced examples are not document headings.
        headings_only=re.sub(r'```.*?```','',part,flags=re.S)
        for m in re.finditer(r'^(#{1,2}) (.+)$',headings_only,re.M):
            title=m[2];name=f'h{counter:03}';counter+=1
            heading_ids[title]=(name,counter);toc.append((title,len(m[1]),name,page_map.get(title,page_no)))
    split=(len(toc)+1)//2
    doc=Document();sec=doc.sections[0];sec.page_width=Cm(21);sec.page_height=Cm(29.7)
    sec.top_margin=Cm(2);sec.bottom_margin=Cm(2);sec.left_margin=Cm(3);sec.right_margin=Cm(2)
    sec.header_distance=Cm(.9);sec.footer_distance=Cm(.9);sec.different_first_page_header_footer=True
    normal=doc.styles['Normal'];set_font(normal,13);normal.paragraph_format.line_spacing=1.15;normal.paragraph_format.space_after=Pt(6);normal.paragraph_format.first_line_indent=Cm(.7);normal.paragraph_format.widow_control=True
    for name,size in [('Title',20),('Heading 1',17),('Heading 2',14),('Heading 3',13)]:
        st=doc.styles[name];set_font(st,size,True);st.paragraph_format.first_line_indent=Cm(0);st.paragraph_format.space_before=Pt(8);st.paragraph_format.space_after=Pt(9);st.paragraph_format.keep_with_next=True
    set_font(doc.styles['Caption'],11);doc.styles['Caption'].paragraph_format.first_line_indent=Cm(0)
    hp=sec.header.paragraphs[0];hp.alignment=WD_ALIGN_PARAGRAPH.RIGHT;hp.paragraph_format.first_line_indent=Cm(0);hr=hp.add_run('Hệ thống đặt vé sự kiện đa hình thức');hr.font.size=Pt(9);hr.font.color.rgb=RGBColor(0,0,0)
    fp=sec.footer.paragraphs[0];fp.alignment=WD_ALIGN_PARAGRAPH.CENTER;fp.paragraph_format.first_line_indent=Cm(0);field(fp,'PAGE',2)
    settings=doc.settings.element;upd=OxmlElement('w:updateFields');upd.set(qn('w:val'),'true');settings.append(upd)
    figures_n=0;tables_n=0
    for pn,(slug,part) in enumerate(parts,1):
        if pn>1:doc.add_page_break()
        if slug=='cover':
            lines=[l for l in part.splitlines() if l.strip()]
            for ix,line in enumerate(lines):
                p=doc.add_paragraph(style='Title' if ix==0 else 'Normal');p.alignment=WD_ALIGN_PARAGRAPH.CENTER;p.paragraph_format.first_line_indent=Cm(0)
                p.paragraph_format.space_before=Pt(22 if ix in [0,3] else 7);p.paragraph_format.space_after=Pt(18 if ix in [0,3] else 8)
                r=p.add_run(line.lstrip('# '));r.font.size=Pt(17 if ix==3 else 13);r.bold=ix in [0,1,2,3]
            continue
        lines=part.strip().splitlines();i=0
        while i<len(lines):
            line=lines[i].strip()
            if not line:i+=1;continue
            if line in ['{{TOC_FIRST}}','{{TOC_SECOND}}']:
                entries=toc[:split] if line=='{{TOC_FIRST}}' else toc[split:]
                for title,level,name,page in entries:
                    p=doc.add_paragraph();p.paragraph_format.first_line_indent=Cm(0);p.paragraph_format.left_indent=Cm(.3 if level==2 else 0);p.paragraph_format.space_after=Pt(8);p.paragraph_format.line_spacing=1.05
                    p.paragraph_format.tab_stops.add_tab_stop(Cm(15.8),WD_TAB_ALIGNMENT.RIGHT,WD_TAB_LEADER.DOTS)
                    r=p.add_run(title+'\t');r.font.size=Pt(11);r.bold=level==1;field(p,'PAGEREF '+name+' \\h',page)
                i+=1;continue
            if line.startswith('#'):
                m=re.match(r'(#+) (.*)',line);title=m[2];p=doc.add_paragraph(title,style='Heading '+str(min(len(m[1]),3)))
                if title in heading_ids:name,ix=heading_ids[title];bookmark(p,name,ix)
                i+=1;continue
            if line.startswith('```'):
                language=line[3:];i+=1;code=[]
                while i<len(lines) and not lines[i].strip().startswith('```'):code.append(lines[i]);i+=1
                p=doc.add_paragraph();p.paragraph_format.first_line_indent=Cm(0);p.paragraph_format.space_after=Pt(8);p.paragraph_format.line_spacing=1.05
                for ix,l in enumerate(code):
                    r=p.add_run(l+('\n' if ix<len(code)-1 else ''));r.font.name='Consolas';r.font.size=Pt(9.5)
                i+=1;continue
            if line.startswith('|'):
                table=[]
                while i<len(lines) and lines[i].strip().startswith('|'):table.append(lines[i]);i+=1
                add_table(doc,table);tables_n+=1;continue
            if line.startswith('!['):
                m=re.match(r'!\[(.*?)\]\((.*?)\)',line);p=doc.add_paragraph();p.alignment=WD_ALIGN_PARAGRAPH.CENTER;p.paragraph_format.first_line_indent=Cm(0);p.paragraph_format.keep_with_next=True
                r=p.add_run();r.add_picture(str(OUT/m[2]),width=Cm(16));inline=r._r.xpath('.//wp:docPr')[0];inline.set('descr',m[1])
                cap=doc.add_paragraph(m[1],style='Caption');cap.alignment=WD_ALIGN_PARAGRAPH.CENTER;figures_n+=1;i+=1;continue
            para=line;i+=1
            while i<len(lines) and lines[i].strip() and not re.match(r'^(#|\||```|!\[|\{\{)',lines[i].strip()):para+=' '+lines[i].strip();i+=1
            p=doc.add_paragraph(para.replace('`',''));p.alignment=WD_ALIGN_PARAGRAPH.JUSTIFY
            if para.startswith('Nguồn '):
                p.paragraph_format.first_line_indent=Cm(0);p.paragraph_format.space_after=Pt(8)
                for run in p.runs:run.font.size=Pt(10)
            if slug=='references':
                p.paragraph_format.first_line_indent=Cm(0);p.paragraph_format.line_spacing=1.05;p.paragraph_format.space_after=Pt(6)
                for run in p.runs:run.font.size=Pt(10.5)
    doc.core_properties.title='Hệ thống đặt vé sự kiện đa hình thức và phục hồi giao dịch bằng EDA'
    doc.core_properties.subject='Báo cáo đồ án T10.3'
    doc.core_properties.author='Lê Hữu Hào';doc.core_properties.last_modified_by=''
    from datetime import datetime, timezone
    doc.core_properties.created=datetime.now(timezone.utc)
    doc.core_properties.modified=datetime.now(timezone.utc)
    doc.core_properties.keywords='PostgreSQL, Kafka, idempotency, outbox, phục hồi'
    # Stock templates can carry an inherited title rule or theme font. Remove
    # paragraph borders and all theme font overrides, including their defaults.
    for tree in [doc.element,doc.styles.element]:
        for border in list(tree.xpath('.//w:pBdr')):border.getparent().remove(border)
        for fonts in tree.xpath('.//w:rFonts'):
            for attr in ['asciiTheme','hAnsiTheme','eastAsiaTheme','cstheme','csTheme']:
                fonts.attrib.pop(qn('w:'+attr),None)
    for p in doc.paragraphs:
        for run in p.runs:
            if run.font.name!='Consolas':run.font.name='Times New Roman'
    doc.save(OUT/'bao-cao-do-an.docx')
    resolved=text.replace('{{TOC_FIRST}}', '\n'.join(f'{t} .... {p}' for t,l,n,p in toc[:split])).replace('{{TOC_SECOND}}','\n'.join(f'{t} .... {p}' for t,l,n,p in toc[split:]))
    assert not re.search(r'\{\{[^}]+\}\}',resolved)
    (OUT/'BAO_CAO.md').write_text(resolved,encoding='utf-8')
    (QA/'document-structure.json').write_text(json.dumps({'plannedPages':43,'figures':figures_n,'tables':tables_n,
        'tocEntries':[dict(title=t,bookmark=n,page=p,level=l) for t,l,n,p in toc],
        'placeholders':re.findall(r'\[CẦN ĐIỀN[^\]]*\]',resolved),'sourceWords':len(resolved.split())},ensure_ascii=False,indent=2),encoding='utf-8')
    print(f'Created DOCX and Markdown: 43 planned pages, {figures_n} figures, {tables_n} tables, {len(resolved.split())} whitespace words')

if __name__=='__main__':main()
