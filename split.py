import os, re

files_to_split = [
    r"c:\joud_erp\joud-hr\src\main\java\com\joud\hr\entity\HrEntities.java",
    r"c:\joud_erp\joud-inventory\src\main\java\com\joud\inventory\bean\ExtraInventoryBeans.java",
    r"c:\joud_erp\joud-inventory\src\main\java\com\joud\inventory\bean\InventoryBeans.java",
    r"c:\joud_erp\joud-inventory\src\main\java\com\joud\inventory\entity\InventoryEntities.java",
    r"c:\joud_erp\joud-trade\src\main\java\com\joud\trade\entity\TradeEntities.java"
]

def split_file(filepath):
    if not os.path.exists(filepath): 
        print(f"Not found: {filepath}")
        return
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # Match package
    pkg_match = re.search(r'^package\s+.*?;', content, re.MULTILINE)
    header = (pkg_match.group(0) + "\n\n") if pkg_match else ""
    
    # Match imports
    imports = re.findall(r'^import\s+.*?;', content, re.MULTILINE)
    if imports:
        header += "\n".join(imports) + "\n\n"

    idx = 0
    while True:
        # Match public class/enum/interface
        # Using a regex that finds public class etc.
        match = re.search(r'public\s+(class|enum|interface)\s+(\w+)', content[idx:])
        if not match:
            break
            
        start_match = idx + match.start()
        
        # Backtrack to find annotations and comments
        prefix_start = start_match
        while prefix_start > 0:
            prev_nl = content.rfind('\n', 0, prefix_start - 1)
            if prev_nl == -1: prev_nl = 0
            line = content[prev_nl:prefix_start].strip()
            if not line or line.startswith('//') or line.startswith('/*') or line.startswith('*') or line.startswith('*/') or line.startswith('@'):
                prefix_start = prev_nl
            else:
                break
                
        # Brace counting
        class_body_start = content.find('{', start_match)
        if class_body_start == -1:
            break
            
        braces = 1
        curr = class_body_start + 1
        in_string, in_char, in_comment, in_line_comment = False, False, False, False
        
        while curr < len(content) and braces > 0:
            c = content[curr]
            prev = content[curr-1] if curr > 0 else ''
            
            if in_line_comment:
                if c == '\n': in_line_comment = False
            elif in_comment:
                if c == '/' and prev == '*': in_comment = False
            elif in_string:
                if c == '"' and prev != '\\': in_string = False
            elif in_char:
                if c == "'" and prev != '\\': in_char = False
            else:
                if c == '/' and curr+1 < len(content) and content[curr+1] == '/':
                    in_line_comment = True
                elif c == '/' and curr+1 < len(content) and content[curr+1] == '*':
                    in_comment = True
                elif c == '"':
                    in_string = True
                elif c == "'":
                    in_char = True
                elif c == '{':
                    braces += 1
                elif c == '}':
                    braces -= 1
            curr += 1
            
        class_content = content[prefix_start:curr].strip()
        class_name = match.group(2)
        
        out_path = os.path.join(os.path.dirname(filepath), class_name + '.java')
        with open(out_path, 'w', encoding='utf-8') as out_f:
            out_f.write(header + class_content + "\n")
            
        print(f"Created {out_path}")
        idx = curr

    os.remove(filepath)
    print(f"Removed {filepath}")

for f in files_to_split:
    split_file(f)
