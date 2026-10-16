const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {spawnSync}=require('node:child_process');

test('asset version stays identical across LF and CRLF checkouts',()=>{
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'khoflow-version-'));
  try {
    fs.mkdirSync(path.join(root,'scripts'));
    fs.mkdirSync(path.join(root,'web/assets/shared'),{recursive:true});
    const script=path.join(root,'scripts/version-frontend.cjs');
    fs.copyFileSync(path.join(__dirname,'../../scripts/version-frontend.cjs'),script);
    const asset=path.join(root,'web/assets/shared/example.js'),index=path.join(root,'web/index.html');
    fs.writeFileSync(asset,'const x = 1;\nconst y = 2;\n');
    fs.writeFileSync(index,'<script src="assets/shared/example.js"></script>');
    const run=(args=[])=>{const result=spawnSync(process.execPath,[script,...args],{encoding:'utf8'});assert.equal(result.status,0,result.stderr);};
    run();const expected=fs.readFileSync(index,'utf8');
    fs.writeFileSync(asset,'const x = 1;\r\nconst y = 2;\r\n');
    run(['--check']);run();assert.equal(fs.readFileSync(index,'utf8'),expected);
    fs.writeFileSync(asset,'const x = 9;\nconst y = 2;\n');
    run();assert.notEqual(fs.readFileSync(index,'utf8'),expected,'actual content changes must invalidate cache');
  } finally {fs.rmSync(root,{recursive:true,force:true});}
});
