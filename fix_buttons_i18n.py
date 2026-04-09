import os
import re

def fix_buttons_i18n(directory):
    replacements = [
        (r'<button([^>]*?)id="[^"]*?(?:[Cc]ancel|[Cc]lose)Btn"[^>]*?>\s*(?:<i[^>]*?></i>\s*)?Cancel\s*</button>', 
         r'<button\1id="cancelBtn" data-i18n="button.cancel">Cancel</button>'),
        
        (r'<button([^>]*?)id="[^"]*?(?:[Cc]ancel|[Cc]lose)Btn"[^>]*?>\s*(?:<i[^>]*?></i>\s*)?Close\s*</button>', 
         r'<button\1id="closeBtn" data-i18n="button.close">Close</button>'),
        
        (r'<button([^>]*?)id="[^"]*?[Ss]aveBtn"[^>]*?>\s*(?:<i[^>]*?></i>\s*)?Save\s*</button>', 
         r'<button\1id="saveBtn" data-i18n="button.save">Save</button>'),
        
        (r'<button([^>]*?)id="[^"]*?(?:[Ss]aveAndCloseBtn|[Ee]ditSaveCloseBtn)"[^>]*?>\s*(?:<i[^>]*?></i>\s*)?Save & Close\s*</button>', 
         r'<button\1id="saveAndCloseBtn" data-i18n="button.saveAndClose">Save & Close</button>'),
         
        (r'<button([^>]*?)id="[^"]*?[Ss]aveAndSubmitBtn"[^>]*?>\s*(?:<i[^>]*?></i>\s*)?Save & Submit\s*</button>', 
         r'<button\1id="saveAndSubmitBtn" data-i18n="button.saveAndSubmit">Save & Submit</button>'),
    ]

    # More generic ones if the ID doesn't match but text does and it's a button
    generic_replacements = [
        (r'<button([^>]*?)>\s*(?:<i[^>]*?></i>\s*)?Save\s*</button>', 
         r'<button\1 data-i18n="button.save">Save</button>'),
        (r'<button([^>]*?)>\s*(?:<i[^>]*?></i>\s*)?Close\s*</button>', 
         r'<button\1 data-i18n="button.close">Close</button>'),
        (r'<button([^>]*?)>\s*(?:<i[^>]*?></i>\s*)?Cancel\s*</button>', 
         r'<button\1 data-i18n="button.cancel">Cancel</button>'),
        (r'<button([^>]*?)>\s*(?:<i[^>]*?></i>\s*)?Save & Close\s*</button>', 
         r'<button\1 data-i18n="button.saveAndClose">Save & Close</button>'),
        (r'<button([^>]*?)>\s*(?:<i[^>]*?></i>\s*)?Save & Submit\s*</button>', 
         r'<button\1 data-i18n="button.saveAndSubmit">Save & Submit</button>'),
    ]

    for root, dirs, files in os.walk(directory):
        for file in files:
            if file.endswith('.html'):
                file_path = os.path.join(root, file)
                with open(file_path, 'r', encoding='utf-8') as f:
                    content = f.read()
                
                original_content = content
                
                # Apply replacements only if data-i18n is not already present on that button
                # This is tricky with regex. Let's do it carefully.
                
                def replace_func(match, key, text):
                    tag_open = match.group(0)
                    if 'data-i18n=' in tag_open:
                        return tag_open
                    # Reconstruct button
                    attrs = match.group(1)
                    # Check if it has an icon
                    icon_match = re.search(r'<i[^>]*?></i>', tag_open)
                    icon_str = icon_match.group(0) + ' ' if icon_match else ''
                    
                    # Try to preserve attributes but add data-i18n
                    return f'<button{attrs} data-i18n="{key}">{icon_str}{text}</button>'

                # Using a simpler strategy: just regex replace if "data-i18n" is missing in the match
                
                new_content = content
                
                # Save & Close
                new_content = re.sub(r'<button([^>]*?)>\s*(<i[^>]*?></i>\s*)?Save & Close\s*</button>', 
                                   lambda m: m.group(0) if 'data-i18n' in m.group(0) else f'<button{m.group(1)} data-i18n="button.saveAndClose">{m.group(2) or ""}Save & Close</button>', 
                                   new_content)
                
                # Save & Submit
                new_content = re.sub(r'<button([^>]*?)>\s*(<i[^>]*?></i>\s*)?Save & Submit\s*</button>', 
                                   lambda m: m.group(0) if 'data-i18n' in m.group(0) else f'<button{m.group(1)} data-i18n="button.saveAndSubmit">{m.group(2) or ""}Save & Submit</button>', 
                                   new_content)

                # Save
                new_content = re.sub(r'<button([^>]*?)>\s*(<i[^>]*?></i>\s*)?Save\s*</button>', 
                                   lambda m: m.group(0) if 'data-i18n' in m.group(0) else f'<button{m.group(1)} data-i18n="button.save">{m.group(2) or ""}Save</button>', 
                                   new_content)
                
                # Close
                new_content = re.sub(r'<button([^>]*?)>\s*(<i[^>]*?></i>\s*)?Close\s*</button>', 
                                   lambda m: m.group(0) if 'data-i18n' in m.group(0) else f'<button{m.group(1)} data-i18n="button.close">{m.group(2) or ""}Close</button>', 
                                   new_content)

                # Cancel
                new_content = re.sub(r'<button([^>]*?)>\s*(<i[^>]*?></i>\s*)?Cancel\s*</button>', 
                                   lambda m: m.group(0) if 'data-i18n' in m.group(0) else f'<button{m.group(1)} data-i18n="button.cancel">{m.group(2) or ""}Cancel</button>', 
                                   new_content)

                if new_content != original_content:
                    with open(file_path, 'w', encoding='utf-8') as f:
                        f.write(new_content)
                    print(f"Fixed buttons in {file_path}")

if __name__ == "__main__":
    fix_buttons_i18n(r'd:\BUDG_V2\src\main\webapp')
