import codecs
import re

files = [
    r'w:\CodeDeX\DeX\MSIX_Source\bin\Modules\UIComponents.ps1',
    r'w:\CodeDeX\DeX\MSIX_Source\bin\Modules\Bindings_Settings.ps1',
    r'w:\CodeDeX\DeX\MSIX_Source\bin\Modules\Bindings_Window.ps1'
]

for file in files:
    with open(file, 'rb') as f:
        b = f.read()
    
    if b.startswith(codecs.BOM_UTF8):
        b = b[3:]
    
    try:
        s = b.decode('utf-8')
    except:
        s = b.decode('latin1')

    if 'Bindings_Settings' in file:
        s = re.sub(r'(?s)\s*\ = \\.FindName\(\"pbPinTimeout\"\)\s*if \(\\) \{\s*\\.BeginAnimation\(\[System\.Windows\.Controls\.Primitives\.RangeBase\]::ValueProperty, \\)\s*\\.Value = 100\s*\}\s*\n', '\n             = .FindName(\"txtPinTimeout\")\n            if () { .Text = \"\" }\n', s)
    elif 'Bindings_Window' in file:
        s = re.sub(r'(?s)\s*\# Reset progress bar to full \(100\)\s*\ = \\.FindName\(\"pbPinTimeout\"\)\s*if \(\\) \{\s*\\.BeginAnimation\(\[System\.Windows\.Controls\.Primitives\.RangeBase\]::ValueProperty, \\)\s*\\.Value = 100\s*\}\s*\n', '\n                     = .FindName(\"txtPinTimeout\")\n                    if () { .Text = \"\" }\n', s)
    
    with open(file, 'wb') as f:
        f.write(codecs.BOM_UTF8 + s.encode('utf-8'))
