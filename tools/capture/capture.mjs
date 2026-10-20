// usage: node capture.mjs <list.json> <outdir>   ; list = [[name,url],...] ; env HEADLESS=1 to try headless first
import { chromium } from 'playwright';
import fs from 'node:fs'; import path from 'node:path';
const [,, listFile, outDir] = process.argv;
const list = JSON.parse(fs.readFileSync(listFile,'utf8'));
fs.mkdirSync(path.join(outDir,'rendered'),{recursive:true});
async function run(headless){
  const browser = await chromium.launch({ headless });
  const ctx = await browser.newContext({ locale:'en-AU', timezoneId:'Australia/Adelaide', viewport:{width:1280,height:900} });
  const page = await ctx.newPage();
  const results=[];
  for (const [name,url] of list){
    let status='ERR', len=0, note='';
    try{
      const resp = await page.goto(url,{waitUntil:'domcontentloaded',timeout:60000});
      status=resp.status();
      for(let i=0;i<10 && /just a moment/i.test(await page.title());i++) await page.waitForTimeout(2000);
      const ctype = resp.headers()['content-type']||'';
      // RAW server body is what an HTTP client + jsoup will see; the rendered DOM (after JS) is saved separately for reference only
      const body = (await resp.body()).toString('utf8');
      if (/just a moment/i.test(body.slice(0,2000))) note='CHALLENGE';
      len=body.length;
      const ext = /json/.test(ctype)?'json':(/xml/.test(ctype)?'xml':(/html/.test(ctype)?'html':'txt'));
      fs.writeFileSync(path.join(outDir,name+'.'+ext), body);
      if (/html/.test(ctype)) fs.writeFileSync(path.join(outDir,'rendered',name+'.rendered.html'), await page.content());
      note += ' '+ctype.split(';')[0];
    }catch(e){ note=e.message.split('\n')[0]; }
    results.push({name,status,len,note});
    console.log(name, status, len, note);
    await page.waitForTimeout(1500); // be polite
  }
  await browser.close();
  return results;
}
let res = await run(process.env.HEADLESS==='1');
if (res.some(r=>r.note.includes('CHALLENGE')||r.status===403) && process.env.HEADLESS==='1'){ console.log('--- challenged headless, retrying headed ---'); res = await run(false); }
