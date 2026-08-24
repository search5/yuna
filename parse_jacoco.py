import re
import sys

files = [
    "com.github.search5.yona.web/UserController.kt.html",
    "com.github.search5.yona.web/OrganizationViewController.kt.html",
    "com.github.search5.yona.web/AttachmentController.kt.html",
    "com.github.search5.yona.domain.mail/IncomingMailProcessingService.kt.html",
    "com.github.search5.yona.domain.mail/MailServiceImpl.kt.html"
]

base_dir = "/home/jiho/yona-convert/yuna/build/reports/jacoco/test/html/"

for f in files:
    path = base_dir + f
    try:
        with open(path, 'r') as file:
            content = file.read()
            print(f"--- {f} ---")
            
            lines = re.findall(r'<span class="[npb]c?[a-z]*"[^>]*id="L(\d+)"[^>]*>(?:<span class="[^"]+" title="([^"]+)">)?', content)
            
            # Wait, the structure is usually <span class="nc" id="L123">...</span>
            # Or <span class="pc bpc" id="L123" title="1 of 2 branches missed.">...</span>
            # Let's just find all title="...missed..."
            
            missed_branches = re.findall(r'id="L(\d+)"><span class="[^"]+" title="([^"]+missed[^"]+)"', content)
            missed_lines = re.findall(r'<span class="nc" id="L(\d+)">', content)
            
            print("Missed Lines:", missed_lines)
            print("Missed Branches:", missed_branches)
            print()
    except Exception as e:
        print(f"Error reading {path}: {e}")
