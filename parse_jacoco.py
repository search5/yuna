import xml.etree.ElementTree as ET
import os

xml_path = "build/reports/jacoco/test/jacocoTestReport.xml"
if not os.path.exists(xml_path):
    print("XML report not found")
    exit(0)

tree = ET.parse(xml_path)
root = tree.getroot()

classes = [
    "com/github/search5/yona/domain/attachment/AttachmentServiceImpl",
    "com/github/search5/yona/domain/board/PostingServiceImpl",
    "com/github/search5/yona/config/oauth2/CustomOAuth2UserService",
    "com/github/search5/yona/config/oauth2/YonaOAuth2User",
    "com/github/search5/yona/config/oauth2/OAuth2UserInfoFactory"
]

for cls in root.findall(".//class"):
    name = cls.get("name")
    if name in classes:
        print(f"Class: {name}")
        for counter in cls.findall("counter"):
            ctype = counter.get("type")
            if ctype in ["LINE", "BRANCH", "METHOD"]:
                missed = int(counter.get("missed"))
                covered = int(counter.get("covered"))
                total = missed + covered
                pct = int((covered / total) * 100) if total > 0 else 100
                print(f"  {ctype}: {pct}% ({covered}/{total})")
