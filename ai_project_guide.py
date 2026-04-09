import os
from tqdm import tqdm

SRC_DIR = "src"
OUT_FILE = "D:/BUDG_V2/docs/PROJECT_GUIDE_SIMPLE.md"

def summarize_file(path):
    summary = []
    with open(path, encoding="utf8", errors="ignore") as f:
        lines = f.readlines()

    # نجيب أول 10 تعليقات في الملف
    comments = [line.strip() for line in lines if line.strip().startswith(("//", "/*", "*"))][:10]
    summary.extend(comments)

    # نجيب أسماء الدوال الرئيسية
    functions = [line.strip() for line in lines if line.strip().startswith(("def ", "public ", "private "))][:5]
    if functions:
        summary.append("\nFunctions/Methods:")
        summary.extend(functions)

    return "\n".join(summary) if summary else "No comments or functions found."

os.makedirs("docs", exist_ok=True)

with open(OUT_FILE, "w", encoding="utf8") as out:
    out.write("# PROJECT TECHNICAL GUIDE (Simple Version)\n\n")
    for root, _, files in os.walk(SRC_DIR):
        for file in tqdm(files):
            if file.endswith((".java", ".ts", ".tsx", ".py")):
                full = os.path.join(root, file)
                try:
                    summary = summarize_file(full)
                    out.write(f"## {full}\n{summary}\n\n")
                except Exception as e:
                    print("Skipped:", full, e)
