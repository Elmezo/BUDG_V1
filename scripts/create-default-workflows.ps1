# Script to create default workflow files manually
# This script creates default workflow BPMN files for all facets

$facets = @(
    "Business Area",
    "Capability", 
    "Glossary",
    "Data Sets",
    "Client",
    "Committee",
    "Policy",
    "Process",
    "Product",
    "Project",
    "Regulation",
    "System",
    "Interface"
)

$bpmnDir = "data\bpmn"
$templatePath = "c:\Users\PC\Downloads\workflow.bpmn"

# Ensure directory exists
if (-not (Test-Path $bpmnDir)) {
    New-Item -ItemType Directory -Path $bpmnDir -Force
    Write-Host "Created directory: $bpmnDir"
}

# Read template if it exists
$template = $null
if (Test-Path $templatePath) {
    $template = Get-Content $templatePath -Raw -Encoding UTF8
    Write-Host "Loaded template from: $templatePath"
} else {
    Write-Host "Template not found at: $templatePath"
    Write-Host "Please ensure the template file exists or update the path in this script."
    exit 1
}

# Create workflow file for each facet
foreach ($facet in $facets) {
    $workflowName = "DefaultWorkflow-1-$facet"
    $filename = "$workflowName.bpmn"
    $filePath = Join-Path $bpmnDir $filename
    
    if (Test-Path $filePath) {
        Write-Host "File already exists: $filename (skipping)"
        continue
    }
    
    # Replace Client with current facet name in template
    $content = $template -replace "DefaultWorkflow-1-Client", $workflowName
    $content = $content -replace "Client", $facet
    
    # Save file
    $content | Out-File -FilePath $filePath -Encoding UTF8 -NoNewline
    Write-Host "Created: $filename"
}

Write-Host "`nDone! Created default workflow files in: $bpmnDir"
Write-Host "Files created:"
Get-ChildItem $bpmnDir -Filter "DefaultWorkflow-*.bpmn" | ForEach-Object { Write-Host "  - $($_.Name)" }

