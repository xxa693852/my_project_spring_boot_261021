"""Generate offline bilingual schema documentation from schema.json (metadata only)."""
import json
import html
import re
import base64
from pathlib import Path

ROOT = Path(__file__).resolve().parent
schema = json.loads((ROOT / 'schema.json').read_text(encoding='utf-8'))
tables = {t['TABLE_NAME']: t for t in schema['tables']}
columns = {t: [c for c in schema['columns'] if c['TABLE_NAME'] == t] for t in tables}
fks = schema['foreignKeys']
names = {t: v['TABLE_COMMENT'] for t, v in tables.items()}
names.update(comment_write_limits='留言頻率限制', flyway_schema_history='Flyway 資料庫版本紀錄',
             refund_notifications='退款通知佇列', registration_cancellation_emails='個別取消報名 Email 通知',
             registration_cancellation_lines='個別取消報名 LINE 通知')
extra = {
 'comment_write_limits': {'member_id':'會員編號', 'window_start':'計次區間起點（毫秒時間戳）',
   'attempts':'區間內嘗試次數', 'last_attempt':'上次嘗試時間（毫秒時間戳）', 'last_write':'上次預約寫入時間（毫秒時間戳）'},
 'flyway_schema_history': {'installed_rank':'套用順序（主鍵）', 'version':'版本號', 'description':'版本說明',
   'type':'遷移類型', 'script':'SQL 腳本檔名', 'checksum':'腳本校驗碼', 'installed_by':'執行的資料庫使用者',
   'installed_on':'套用時間', 'execution_time':'執行耗時（毫秒）', 'success':'是否執行成功'},
 'payments': {'refunded_at':'後台標記已退款時間（不代表玩家入帳時間）'},
}
def cn(t, c):
    return extra.get(t, {}).get(c['COLUMN_NAME'], c['COLUMN_COMMENT'])
def short(t, c):
    s = cn(t, c)
    if c['COLUMN_NAME'] == 'id': return '主鍵編號'
    if c['COLUMN_NAME'] == 'status': return '狀態（詳見欄位對照）'
    if t == 'payments' and c['COLUMN_NAME'] == 'refunded_at': return '後台標記已退款時間'
    s = re.split('[（；，]', s)[0]
    return s if len(s) <= 19 else s[:18] + '…'
def unique_indexes(t):
    result = {}
    for i in schema['indexes']:
        if i['TABLE_NAME'] == t and i['NON_UNIQUE'] == '0':
            result.setdefault(i['INDEX_NAME'], []).append(i['COLUMN_NAME'])
    return result
def flags(t, c):
    result = []
    if c['COLUMN_KEY'] == 'PRI': result.append('PK')
    if any(f['TABLE_NAME'] == t and f['COLUMN_NAME'] == c['COLUMN_NAME'] for f in fks): result.append('FK')
    if any(v == [c['COLUMN_NAME']] for k,v in unique_indexes(t).items() if k != 'PRIMARY'): result.append('UQ')
    return '/'.join(result)
def esc(s): return html.escape(str(s), quote=True)

palette = {'core':'#1e5949', 'cancel':'#79532d', 'account':'#345e83', 'support':'#665485'}
groups = {
 'core':['members','events','registrations','payments','refund_notifications'],
 'cancel':['registration_cancellations','registration_cancellation_emails','registration_cancellation_lines'],
 'account':['member_suspension_actions','password_reset_tokens','event_comments'],
 'support':['auth_tokens','event_cancellation_notifications','event_reminder_deliveries',
            'comment_moderation_notifications','comment_write_limits','flyway_schema_history']}
def group(t): return next(g for g, ts in groups.items() if t in ts)

def diagram(filename, title, subtitle, nodes, edges, width, height, compact=False):
    # Edges are drawn in the gutters before table cards. A pair of numbers at
    # endpoints gives the exact parent/child multiplicity without ambiguous arrows.
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
      '<style>text{font-family:"Microsoft JhengHei","Noto Sans CJK TC",Arial,sans-serif} .en{font-family:Consolas,monospace}</style>',
      f'<rect width="{width}" height="{height}" fill="#f2f5f3"/>',
      f'<text x="40" y="48" font-size="29" font-weight="700" fill="#173d32">{esc(title)}</text>',
      f'<text x="40" y="80" font-size="16" fill="#52635d">{esc(subtitle)}</text>']
    for path, label, lx, ly, a, b, mult in edges:
        out.append(f'<path d="{path}" fill="none" stroke="#7b8f87" stroke-width="2.4"/>')
        out.append(f'<text x="{a[0]}" y="{a[1]}" font-size="15" fill="#254c3d">1</text>')
        out.append(f'<text x="{b[0]}" y="{b[1]}" font-size="15" fill="#254c3d">{mult}</text>')
        out.append(f'<text x="{lx}" y="{ly}" font-size="14" font-weight="600" fill="#52635d">{esc(label)}</text>')
    for t, x, y, stub in nodes:
        w = 350 if compact else 640
        cs = columns[t]
        if compact: cs = [c for c in cs if flags(t,c)]
        if stub: cs = [c for c in cs if c['COLUMN_NAME']=='id']
        row = 28 if compact else 37
        h = 82 + len(cs)*row + 12
        color = palette[group(t)]
        out += [f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="12" fill="white" stroke="#ced9d3"/>',
          f'<path d="M{x+12} {y} H{x+w-12} Q{x+w} {y} {x+w} {y+12} V{y+76} H{x} V{y+12} Q{x} {y} {x+12} {y}" fill="{color}"/>',
          f'<text class="en" x="{x+16}" y="{y+29}" font-size="{16 if compact else 20}" fill="white" font-weight="700">{esc(t)}</text>',
          f'<text x="{x+16}" y="{y+58}" font-size="{15 if compact else 18}" fill="#eff6f2">{esc(names[t])}{"（關聯參照）" if stub else ""}</text>']
        for i,c in enumerate(cs):
            yy = y+82+i*row
            if i%2 == 0: out.append(f'<rect x="{x+1}" y="{yy}" width="{w-2}" height="{row}" fill="#f3f7f4"/>')
            out.append(f'<text class="en" x="{x+12}" y="{yy+19}" font-size="11" fill="{color}">{esc(flags(t,c))}</text>')
            if compact:
                out.append(f'<text class="en" x="{x+70}" y="{yy+19}" font-size="13" fill="#263a32">{esc(c["COLUMN_NAME"])}</text>')
            else:
                out.append(f'<text class="en" x="{x+85}" y="{yy+23}" font-size="15" fill="#263a32">{esc(c["COLUMN_NAME"])}</text>')
                out.append(f'<text x="{x+345}" y="{yy+23}" font-size="14" fill="#485e53">{esc(short(t,c))}</text>')
    out.append(f'<text x="40" y="{height-24}" font-size="14" fill="#53675d">PK 主鍵　FK 資料庫外鍵　UQ 單欄唯一｜1：一筆；0..N：零至多筆；0..1：零或一筆｜2026-11-23・V20 結構</text>')
    out.append('</svg>')
    (ROOT/filename).write_text('\n'.join(out), encoding='utf-8')

diagram('01-core.svg','01　會員、活動、報名與收退款','每個英文欄位右側均附中文；完整型別、空值與複合唯一限制請看 HTML 欄位對照。',
 [('members',40,140,False),('events',40,820,False),('registrations',760,140,False),
  ('payments',1480,140,False),('refund_notifications',1480,1080,False)],
 [('M680 250 H760','報名',693,229,(690,270),(711,270),'0..N'),
  ('M680 920 H718 V440 H760','活動的報名',686,900,(688,943),(715,430),'0..N'),
  ('M360 678 V820','建立活動',374,754,(373,699),(373,805),'0..N'),
  ('M1400 300 H1480','付款嘗試',1407,280,(1408,322),(1431,322),'0..N'),
  ('M1800 863 V1080','退款通知（依管道）',1815,967,(1813,890),(1813,1065),'0..N')],2160,1690)

diagram('02-cancellations.svg','02　管理員個別取消報名與通知','member_id 是被取消報名的會員；operator_id 是執行取消的管理員，兩者都對應 members。',
 [('members',40,140,True),('events',760,140,True),('registrations',1480,140,True),
  ('registration_cancellations',760,490,False),('registration_cancellation_emails',40,1120,False),
  ('registration_cancellation_lines',1480,1120,False)],
 [('M360 271 V570 H760','被取消會員／執行管理員（兩條外鍵）',65,542,(373,295),(700,558),'0..N'),
  ('M1080 271 V490','活動的取消歷程',1094,384,(1093,295),(1093,474),'0..N'),
  ('M1800 271 V570 H1400','同一報名可有多次取消歷程',1470,542,(1813,295),(1410,558),'0..N'),
  ('M950 954 V1030 H360 V1120','Email 通知',390,1013,(963,979),(373,1104),'0..1'),
  ('M1210 954 V1030 H1800 V1120','LINE 通知',1580,1013,(1223,979),(1813,1104),'0..1')],2160,1530)

diagram('03-accounts-comments.svg','03　會員安全、停權與留言','actor_member_id 是執行停權操作的管理員；event_comments.member_id 是留言作者。',
 [('member_suspension_actions',40,140,False),('members',760,140,False),
  ('password_reset_tokens',1480,140,False),('event_comments',760,980,False),('events',1480,980,True)],
 [('M760 300 H680','停權對象／管理員（兩條外鍵）',235,125,(734,325),(682,325),'0..N'),
  ('M1400 300 H1480','重設密碼',1408,279,(1407,326),(1430,326),'0..N'),
  ('M1080 678 V980','發表留言',1094,820,(1094,705),(1094,963),'0..N'),
  ('M1480 1080 H1400','活動留言',1407,1060,(1454,1104),(1406,1104),'0..N')],2160,1390)

# Overview uses only keys. Its central flow is deliberately kept small for study.
diagram('00-overview.svg','D&D 活動報名平台｜ER 關聯總覽','總覽只列鍵值；下方三張分區詳圖附完整英文／中文欄位。無實體外鍵的資料表另外列於 HTML。',
 [('members',40,140,False),('events',40,480,False),('registrations',510,140,False),
  ('payments',980,140,False),('refund_notifications',1450,140,False),
  ('registration_cancellations',510,530,False),('registration_cancellation_emails',40,960,False),
  ('registration_cancellation_lines',980,960,False)],
 [('M390 240 H510','報名',423,220,(402,262),(460,262),'0..N'),
  ('M215 346 V480','建立活動',230,415,(228,370),(228,466),'0..N'),
  ('M390 570 H450 V320 H510','活動報名',402,550,(402,595),(464,341),'0..N'),
  ('M860 240 H980','付款嘗試',884,220,(872,262),(930,262),'0..N'),
  ('M1330 240 H1450','退款通知',1354,220,(1342,262),(1400,262),'0..N'),
  ('M685 318 V530','取消歷程',700,455,(698,344),(698,515),'0..N'),
  ('M600 764 V890 H215 V960','Email',260,875,(613,789),(228,945),'0..1'),
  ('M770 764 V890 H1155 V960','LINE',1010,875,(783,789),(1168,945),'0..1')],1840,1210,compact=True)

diagram('04-support.svg','04　權杖、通知、留言限制與系統表','這六張表沒有實體外鍵；編號的邏輯對應與用途請看 HTML 說明，不以實線冒充外鍵關係。',
 [('auth_tokens',40,140,False),('event_cancellation_notifications',760,140,False),
  ('event_reminder_deliveries',1480,140,False),('comment_moderation_notifications',40,850,False),
  ('comment_write_limits',760,850,False),('flyway_schema_history',1480,850,False)],[],2160,1420)

css = '''*{box-sizing:border-box}body{margin:0;background:#f2f5f3;color:#253c32;font-family:"Microsoft JhengHei",Arial,sans-serif;line-height:1.7}main{max-width:1460px;margin:auto;padding:36px 28px}h1{font-size:32px;margin:0}h2{margin-top:42px}p{max-width:1100px}a{color:#1c6854}nav{display:flex;gap:12px;flex-wrap:wrap;margin:22px 0}nav a,.chip{background:#e0ebe4;padding:6px 12px;border-radius:8px;text-decoration:none}figure{margin:22px 0;background:white;border:1px solid #d5e1d9;border-radius:14px;overflow:hidden}figure img{width:100%;display:block}figcaption{padding:12px 20px;color:#52675b}aside{padding:16px 20px;background:#fff7e5;border-left:4px solid #ac7a32;margin:20px 0}.toolbar{position:sticky;top:0;background:#f2f5f3ef;padding:14px 0;z-index:2}input{width:100%;max-width:600px;padding:12px 16px;font:inherit;border:1px solid #a8beb1;border-radius:8px}article{background:white;border:1px solid #d5e1d9;border-radius:12px;padding:22px;margin:20px 0;scroll-margin-top:85px}article h3{margin:0;font-size:21px}code{font-family:Consolas,monospace;overflow-wrap:anywhere}table{border-collapse:collapse;width:100%;font-size:14px;margin-top:15px}th,td{text-align:left;padding:10px 12px;border-bottom:1px solid #e2e9e4;vertical-align:top}th{background:#e9f1ec}tbody tr:nth-child(even){background:#f7faf8}.scroll{overflow-x:auto}td:first-child{min-width:225px}.muted{color:#61776b;font-size:14px}.rel{background:#f0f5f2;padding:12px 16px;border-radius:8px;margin-top:14px}footer{margin-top:32px;color:#61776b}@media print{.toolbar,nav{display:none}article{break-inside:avoid}figure{break-before:page}}'''
parts = ['<!doctype html><html lang="zh-Hant"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">',
 '<title>D&D 專案 ER 圖與中英文欄位對照</title><style>'+css+'</style><main>',
 '<h1>D&D 活動報名平台</h1><p>ER 關聯圖與中英文欄位對照｜2026-11-23｜V20 結構</p>',
 '<p>依本機測試資料庫的結構中繼資料及專案 migration 製作，未讀取會員、付款或權杖的實際資料。共 16 張業務表、1 張 Flyway 系統表；欄位與外鍵以實際資料庫為準。</p>',
 '<nav><a href="#diagrams">看 ER 圖</a><a href="#dictionary">查英文／中文欄位</a><a href="#logical">未設定外鍵的資料表</a></nav>',
 '<aside><b>如何閱讀：</b>PK = Primary Key（主鍵）；FK = Foreign Key（資料庫外鍵）；UQ = Unique（單欄唯一）。1 → 0..N 表示一筆父資料可對應零至多筆子資料；1 → 0..1 表示零或一筆。所有外鍵子欄位目前均不可為 NULL。<br>總覽省略部分交叉關聯以方便閱讀；完整 16 條外鍵均列於下方欄位對照與分區圖。停權與取消歷程有兩個不同角色指向會員，圖上合併路徑並標註「兩條外鍵」。</aside>',
 '<section id="diagrams"><h2>關聯圖</h2>']
for file,title in [('00-overview.svg','主要流程總覽（先記住主要流程）'),('01-core.svg','會員、活動、報名、付款與退款通知'),('02-cancellations.svg','個別取消報名與通知'),('03-accounts-comments.svg','會員安全、停權與留言'),('04-support.svg','權杖、通知、留言限制與系統表（未設實體外鍵）')]:
    encoded = base64.b64encode((ROOT/file).read_bytes()).decode('ascii')
    parts.append(f'<figure><a href="{file}" target="_blank"><img src="data:image/svg+xml;base64,{encoded}" alt="{title}"></a><figcaption>{title}｜點圖可開啟同目錄 SVG 放大，文字不會模糊。圖已內嵌，HTML 單檔也可離線閱讀。</figcaption></figure>')
parts += ['</section><section id="dictionary"><h2>完整欄位對照</h2>',
 '<p>英文右側直接附中文；下表另外保留 SQL 型別、可否留空與關聯。多欄共同組成的唯一限制列於每張表下方，不把複合唯一誤標成每欄各自唯一。</p>',
 '<div class="toolbar"><input id="search" type="search" placeholder="搜尋英文或中文，例如 member、退款、通知" aria-label="搜尋資料表或欄位"><span id="count" class="chip">17 張資料表</span></div>']
logical = {
 'auth_tokens':'owner_type 與 owner_id 共同識別權杖持有者；owner_id 未設資料庫外鍵，不能只看編號就認定為某張表的外鍵。',
 'event_cancellation_notifications':'event_id 邏輯對應 events.id；member_id 邏輯對應 members.id。這張表未設定實體外鍵，活動名稱與時間另存通知快照。',
 'event_reminder_deliveries':'registration_id 邏輯對應 registrations.id，也是本表主鍵，因此每筆報名最多一筆提醒紀錄；未設定實體外鍵。',
 'comment_moderation_notifications':'member_id 邏輯對應 members.id；comment_id 邏輯對應 event_comments.id。未設定實體外鍵，通知保留活動名稱與留言建立時間快照。',
 'comment_write_limits':'member_id 邏輯對應 members.id，也是本表主鍵；未設定實體外鍵。三個 BIGINT 時間欄位是程式 Clock.millis() 的毫秒時間戳，並非 DATETIME。',
 'flyway_schema_history':'Flyway 自動維護的 migration 套用紀錄，不是報名業務資料；不要手動修改。'}
md = ['# D&D 專案 ER 圖與中英文欄位對照','', '結構日期：2026-11-23；本機測試資料庫，對應 V20。16 張業務表 + 1 張 Flyway 系統表。', '',
      '先開啟 [index.html](index.html) 查看 ER 圖與可搜尋的欄位對照。PK：主鍵；FK：資料庫外鍵；UQ：單欄唯一。','']
for t in tables:
    parts.append(f'<article id="{t}"><h3><code>{t}</code>｜{esc(names[t])}</h3>')
    md += [f'## {t}｜{names[t]}','', '| 英文欄位 | 中文說明 | SQL 型別 | 鍵 | 可留空 |','| --- | --- | --- | --- | --- |']
    if t == 'payments':
        parts.append('<aside><b>refunded_at 的實際意義：</b>後台將訂單標記為已退款的時間；不是金流實際執行時間，也不代表玩家帳戶入帳時間。資料庫既有 COMMENT 寫作「退款完成時間」，此處依現行程式行為補充說明，沒有修改資料庫。</aside>')
    parts.append('<div class="scroll"><table><thead><tr><th>英文欄位</th><th>中文說明</th><th>SQL 型別</th><th>鍵</th><th>可留空</th></tr></thead><tbody>')
    for c in columns[t]:
        name = c['COLUMN_NAME']; meaning = cn(t,c); key = flags(t,c) or '—'; nullable = '是' if c['IS_NULLABLE']=='YES' else '否'
        relations = [f for f in fks if f['TABLE_NAME']==t and f['COLUMN_NAME']==name]
        target = ''.join(f'<br><a href="#{f["REFERENCED_TABLE_NAME"]}">→ {f["REFERENCED_TABLE_NAME"]}.{f["REFERENCED_COLUMN_NAME"]}</a>' for f in relations)
        parts.append(f'<tr><td><code>{esc(name)}</code>{target}</td><td>{esc(meaning)}</td><td><code>{esc(c["COLUMN_TYPE"])}</code></td><td>{key}</td><td>{nullable}</td></tr>')
        md.append(f'| {name} | {meaning} | {c["COLUMN_TYPE"]} | {key} | {nullable} |')
    parts.append('</tbody></table></div>')
    relations = [f for f in fks if f['TABLE_NAME']==t]
    reltexts = []
    for f in relations:
        one = any(v==[f['COLUMN_NAME']] for v in unique_indexes(t).values())
        reltexts.append(f'{f["REFERENCED_TABLE_NAME"]}.{f["REFERENCED_COLUMN_NAME"]}（1）→ {t}.{f["COLUMN_NAME"]}（{"0..1" if one else "0..N"}）')
    for k,v in unique_indexes(t).items():
        if len(v)>1: reltexts.append(f'{"複合主鍵" if k=="PRIMARY" else "複合唯一"}：({", ".join(v)})')
    if t in logical: reltexts.append(logical[t])
    if reltexts:
        parts.append('<div class="rel">'+'<br>'.join(esc(s) for s in reltexts)+'</div>')
        md += ['', *['- '+s for s in reltexts]]
    if t == 'payments': md += ['', '> refunded_at 表示後台標記已退款時間，不代表金流執行或玩家入帳時間。']
    parts.append('</article>'); md.append('')
parts += ['</section><section id="logical"><h2>沒有外鍵線，仍可能有業務關聯</h2>',
 '<p>下列資料表以編號或快照支援通知、登入與留言限制，但未設定實體外鍵，所以未畫成實線。資料表未連線不代表沒使用，也不代表應直接新增外鍵。</p><ul>']
for t,s in logical.items():
    if t != 'flyway_schema_history': parts.append(f'<li><a href="#{t}"><code>{t}</code>｜{names[t]}</a>：{esc(s)}</li>')
parts += ['</ul></section><footer>來源：information_schema 結構中繼資料、V1–V20 migration、CommentWriteLimiter.java、PaymentLifecycle.java。中文以既有 COMMENT 為基礎，缺少註解者由程式用途補充；schema.json 僅包含結構，沒有資料列、密碼或連線設定。離線可開啟，不依賴外部網站。</footer></main>',
 '''<script>const input=document.getElementById('search');input.addEventListener('input',()=>{const q=input.value.trim().toLowerCase();let n=0;document.querySelectorAll('article').forEach(a=>{const show=a.textContent.toLowerCase().includes(q);a.hidden=!show;if(show)n++});document.getElementById('count').textContent=n+' 張資料表'});</script></html>''']
(ROOT/'index.html').write_text('\n'.join(parts),encoding='utf-8')
(ROOT/'欄位中英文對照.md').write_text('\n'.join(md),encoding='utf-8')
print(f'Generated 5 SVG diagrams and bilingual documentation: {len(tables)} tables, {sum(map(len,columns.values()))} columns, {len(fks)} physical FKs.')
