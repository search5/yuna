import xml.etree.ElementTree as ET

tree = ET.parse('build/reports/jacoco/test/jacocoTestReport.xml')
root = tree.getroot()

classes = [
    "com/github/search5/yona/web/PullRequestViewController",
    "com/github/search5/yona/domain/vcs/BareCommit",
    "com/github/search5/yona/web/OrganizationViewController"
]

for pkg in root.findall('package'):
    for cls in pkg.findall('class'):
        if cls.get('name') in classes:
            print(f"Class: {cls.get('name')}")
            for counter in cls.findall('counter'):
                if counter.get('type') in ['BRANCH', 'LINE', 'METHOD']:
                    missed = int(counter.get('missed'))
                    covered = int(counter.get('covered'))
                    total = missed + covered
                    pct = covered / total * 100 if total > 0 else 100
                    print(f"  {counter.get('type')}: {pct:.2f}% ({covered}/{total})")
            
            # also let's find missing branch lines
            source_file = cls.get('sourcefilename')
            for sf in pkg.findall('sourcefile'):
                if sf.get('name') == source_file:
                    missing_branch_lines = []
                    missing_lines = []
                    for line in sf.findall('line'):
                        if int(line.get('mb', 0)) > 0:
                            missing_branch_lines.append(line.get('nr'))
                        if int(line.get('mi', 0)) > 0 and int(line.get('ci', 0)) == 0:
                            missing_lines.append(line.get('nr'))
                    print(f"  Missing branch lines: {', '.join(missing_branch_lines)}")
                    print(f"  Missing lines: {', '.join(missing_lines)}")

