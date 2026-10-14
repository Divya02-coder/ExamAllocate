/* Configure BACKEND_BASE to the current backend laptop IP. Data is loaded from MySQL via Spring Boot. */
const BACKEND_BASE = 'http://10.164.196.196:8080';
const $ = (id) => document.getElementById(id);
const palette = ['#3d63dd','#8064d9','#19a7a0','#ed9b45','#e15e70','#2c9c70','#52a6db','#b27bc8','#8794a8','#d1b343'];
const charts = {};
let courseData = [];
let cityData = [];
let grievanceData = [];

function message(text, type='') { const el=$('message'); el.textContent=text; el.className='message'+(type?' '+type:''); }
function fmt(n) { return Number(n || 0).toLocaleString(); }
function safeText(value) { return String(value ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch])); }
function drawChart(id, config) {
  if (typeof Chart === 'undefined') { message('Charts could not load. Check your internet connection and reload the page.', 'error'); return; }
  if (charts[id]) charts[id].destroy();
  charts[id] = new Chart($(id), config);
}
function baseOptions() { return {responsive:true,maintainAspectRatio:false,plugins:{legend:{position:'bottom',labels:{usePointStyle:true,boxWidth:8,padding:16,font:{size:11}}}}}; }

// Simple 1-D K-means (k=3) on registration counts: unsupervised grouping, not future prediction.
function kMeansDemand(courses) {
  if (!courses.length) return new Map();
  if (courses.length < 3) return new Map(courses.map((c,i)=>[c.course, courses.length===1?'Medium':(i===0?'Low':'High')]));
  const sorted = [...courses].sort((a,b)=>Number(a.registrations)-Number(b.registrations));
  let centers = [Number(sorted[0].registrations), Number(sorted[Math.floor((sorted.length-1)/2)].registrations), Number(sorted[sorted.length-1].registrations)];
  let labels = [];
  for(let iter=0;iter<30;iter++) {
    labels = courses.map(c => { const v=Number(c.registrations); let best=0; for(let j=1;j<3;j++) if(Math.abs(v-centers[j])<Math.abs(v-centers[best])) best=j; return best; });
    const next=centers.map((old,j)=>{const vals=courses.filter((c,i)=>labels[i]===j).map(c=>Number(c.registrations)); return vals.length?vals.reduce((a,b)=>a+b,0)/vals.length:old;});
    if(next.every((v,j)=>Math.abs(v-centers[j])<0.001)) break; centers=next;
  }
  const ordered = centers.map((v,i)=>({v,i})).sort((a,b)=>a.v-b.v);
  const nameByCluster = new Map([[ordered[0].i,'Low'],[ordered[1].i,'Medium'],[ordered[2].i,'High']]);
  return new Map(courses.map((c,i)=>[c.course,nameByCluster.get(labels[i]) || 'Medium']));
}
function renderTable() {
  const search = $('courseSearch').value.trim().toLowerCase();
  const demand = kMeansDemand(courseData);
  const visible = courseData.filter(c=>String(c.course).toLowerCase().includes(search));
  $('courseRows').innerHTML = visible.length ? visible.map(c=>{
    const group=demand.get(c.course)||'Medium';
    return `<tr><td class="course-name">${safeText(c.course)}</td><td>${fmt(c.registrations)}</td><td class="pct">${Number(c.percentage||0).toFixed(1)}%</td><td>${fmt(c.allocated)}</td><td>${fmt(c.pendingAllocation)}</td><td><span class="pill ${group.toLowerCase()}">${group} demand</span></td></tr>`;
  }).join('') : '<tr><td colspan="6" class="empty">No matching courses found.</td></tr>';
}
function render(data) {
  const s=data.summary||{}; courseData=data.courses||[]; cityData=data.cities||[]; grievanceData=data.grievancesByStatus||[];
  $('totalRegistrations').textContent=fmt(s.totalRegistrations); $('totalCourses').textContent=fmt(s.totalCourses);
  $('totalAllocated').textContent=fmt(s.totalAllocated); $('pendingAllocation').textContent=fmt(s.pendingAllocation);
  $('openGrievances').textContent=fmt(s.openGrievances); $('totalGrievances').textContent=`${fmt(s.totalGrievances)} total grievances`;
  $('pendingAccommodations').textContent=fmt(s.pendingAccommodations);
  const rate=Number(s.totalRegistrations)>0 ? Number(s.totalAllocated)*100/Number(s.totalRegistrations):0;
  $('allocationRate').textContent=`${rate.toFixed(1)}% of registrations allocated`;
  const courses=[...courseData].sort((a,b)=>Number(b.registrations)-Number(a.registrations));
  const opts=baseOptions();
  drawChart('coursePie',{type:'doughnut',data:{labels:courses.map(c=>c.course),datasets:[{data:courses.map(c=>Number(c.registrations)),backgroundColor:courses.map((_,i)=>palette[i%palette.length]),borderWidth:2,borderColor:'#fff'}]},options:{...opts,cutout:'58%',plugins:{...opts.plugins,tooltip:{callbacks:{label:(ctx)=>{const total=ctx.dataset.data.reduce((a,b)=>a+b,0);const p=total?ctx.parsed*100/total:0;return `${ctx.label}: ${ctx.parsed} (${p.toFixed(1)}%)`;}}}}}});
  drawChart('courseBar',{type:'bar',data:{labels:courses.map(c=>c.course),datasets:[{label:'Registrations',data:courses.map(c=>Number(c.registrations)),backgroundColor:courses.map((_,i)=>palette[i%palette.length]),borderRadius:7,maxBarThickness:38}]},options:{...opts,indexAxis:'y',scales:{x:{beginAtZero:true,ticks:{precision:0},grid:{color:'#edf0f6'}},y:{grid:{display:false}}},plugins:{...opts.plugins,legend:{display:false}}}});
  const cities=[...cityData].sort((a,b)=>Number(b.registrations)-Number(a.registrations)).slice(0,8);
  drawChart('cityChart',{type:'bar',data:{labels:cities.map(c=>c.city),datasets:[{label:'Registrations',data:cities.map(c=>Number(c.registrations)),backgroundColor:'#19a7a0',borderRadius:6}]},options:{...opts,scales:{y:{beginAtZero:true,ticks:{precision:0},grid:{color:'#edf0f6'}},x:{grid:{display:false}}},plugins:{...opts.plugins,legend:{display:false}}}});
  drawChart('grievanceChart',{type:'doughnut',data:{labels:grievanceData.map(g=>g.status),datasets:[{data:grievanceData.map(g=>Number(g.count)),backgroundColor:grievanceData.map((_,i)=>palette[i%palette.length]),borderColor:'#fff',borderWidth:2}]},options:opts});
  renderTable();
  const date=data.generatedAt?new Date(data.generatedAt):new Date(); $('lastUpdated').textContent=`Updated ${date.toLocaleTimeString([], {hour:'2-digit',minute:'2-digit',second:'2-digit'})}`;
}
async function loadDashboard() {
  const key=$('adminKey').value.trim();
  if(!key){message('Enter the admin key first.','error');$('adminKey').focus();return;}
  $('connectBtn').disabled=true; $('refreshBtn').disabled=true; message('Connecting to backend and loading current database statistics…');
  try {
    const response=await fetch(`${BACKEND_BASE}/api/analytics/overview`,{method:'GET',headers:{'X-Admin-Key':key,'Accept':'application/json'}});
    const text=await response.text(); let body={}; try{body=text?JSON.parse(text):{};}catch{body={message:text};}
    if(!response.ok) throw new Error(body.message || (response.status===403?'Admin key was rejected. Check the configured key.':`Request failed (${response.status}).`));
    render(body); message('Dashboard loaded successfully from the backend.','success');
  } catch(e) {
    message(`Could not load analytics: ${e.message}. Check that the backend is running, the IP is correct, CORS allows this frontend, and the admin key matches application.properties.`, 'error');
  } finally { $('connectBtn').disabled=false; $('refreshBtn').disabled=false; }
}
$('connectBtn').addEventListener('click',loadDashboard);
$('refreshBtn').addEventListener('click',loadDashboard);
$('toggleKey').addEventListener('click',()=>{const field=$('adminKey');const show=field.type==='password';field.type=show?'text':'password';$('toggleKey').textContent=show?'Hide':'Show';$('toggleKey').setAttribute('aria-label',show?'Hide admin key':'Show admin key');});
$('courseSearch').addEventListener('input',renderTable);
$('adminKey').addEventListener('keydown',e=>{if(e.key==='Enter')loadDashboard();});
