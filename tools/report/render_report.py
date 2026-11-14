"""Canonical DOCX renderer with an isolated Linux LibreOffice conversion adapter.

The Windows Codex primary runtime has no bundled LibreOffice. Its Python and
Poppler still perform all DOCX/PNG work. A disposable container (no network,
ports, product env or volumes) handles only Office layout conversion.
"""
from pathlib import Path
import importlib.util, json, os, subprocess, sys

ROOT=Path(__file__).resolve().parents[2]
RUNTIME=Path.home()/'.cache/codex-runtimes/codex-primary-runtime/dependencies'
SKILL=Path.home()/'.codex/plugins/cache/openai-primary-runtime/documents/26.1112.11217/skills/documents'
sys.path.insert(0,str(SKILL))
spec=importlib.util.spec_from_file_location('canonical_docx_renderer',SKILL/'render_docx.py')
renderer=importlib.util.module_from_spec(spec);spec.loader.exec_module(renderer)
os.environ['PATH']=str(RUNTIME/'native/poppler/Library/bin')+os.pathsep+os.environ['PATH']

def convert(input_path,user_profile,convert_tmp_dir,stem,verbose=False):
    ip=Path(input_path).resolve();op=Path(convert_tmp_dir).resolve()
    cmd=['docker','run','--rm','--network','none','--memory','1200m','--cpus','2',
         '--mount',f'type=bind,source={ip.parent},target=/input,readonly',
         '--mount',f'type=bind,source={op},target=/output',
         '--mount','type=bind,source=C:/Windows/Fonts,target=/usr/local/share/fonts/windows,readonly',
         'ticketing-t103-render:local','libreoffice','-env:UserInstallation=file:///tmp/t103-lo',
         '--headless','--convert-to','pdf:writer_pdf_Export','--outdir','/output','/input/'+ip.name]
    proc=subprocess.run(cmd,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=180)
    debug=proc.stdout+'\n'+proc.stderr
    if verbose:print(debug)
    if proc.returncode:raise RuntimeError(f'Isolated Office renderer exit{proc.returncode}: '+debug)
    result=op/(stem+'.pdf')
    return (str(result) if result.exists() else '',debug)

if __name__=='__main__':
    diagnostic={'primaryPython':sys.executable,'canonicalRenderer':str(SKILL/'render_docx.py'),
                'conversion':'isolated Docker LibreOffice; Windows bundle libreOfficeVersion=null',
                'image':'ticketing-t103-render:local','ports':[],'productVolumes':False}
    (ROOT/'reports/t10-3/render-runtime.json').write_text(json.dumps(diagnostic,indent=2),encoding='utf-8')
    renderer.convert_to_pdf=convert
    renderer.main()
