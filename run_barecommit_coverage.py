import sys
html = sys.stdin.read()
import re
rows = re.findall(r'<tr>(.*?)</tr>', html, re.DOTALL)
for row in rows:
    cols = re.findall(r'<td.*?>(.*?)</td>', row, re.DOTALL)
    if not cols: continue
    name = re.sub('<[^>]*>', '', cols[0])
    missed_branch = cols[3]
    if missed_branch != '0' and missed_branch != 'n/a':
        print(name, missed_branch + ' of ' + cols[4])
