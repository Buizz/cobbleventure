const labels = {z_gems:'일반 Z 젬 수',exclusive_z_gems:'전용 Z 젬 수',mega_money:'메가 연구비',iv_point:'개체값 +1 비용',ev_point:'노력치 +1 비용',dynamax_level_mushrooms:'다이맥스 +1 다이버섯 수',gmax_mushrooms:'거다이맥스 인자 다이버섯 수',blank_z:'빈 Z크리스탈',mega:'미가공 메가스톤',mushroom:'다이버섯'};
let documentData;
const status = document.querySelector('#status');
async function load() {
  try {
    const response = await fetch('/api/laboratory-research');
    if (!response.ok) throw new Error('연구소 설정을 불러오지 못했습니다.');
    documentData = await response.json();
    for (const group of ['costs','materials']) {
      const container = document.getElementById(group); container.replaceChildren();
      for (const [key,value] of Object.entries(documentData[group])) {
        const label = document.createElement('label'); label.textContent = labels[key] || key;
        const input = document.createElement('input'); input.name = `${group}.${key}`; input.value = value;
        input.required = true;
        if (group === 'costs') { input.type='number'; input.min='0'; input.max='1000000'; input.step='1'; }
        label.append(input); container.append(label);
      }
    }
    document.querySelector('#crystals').value = JSON.stringify(documentData.z_crystals,null,2);
    document.querySelector('#excluded').value = JSON.stringify(documentData.dynamax_excluded,null,2);
    document.querySelector('#save').disabled=false; status.textContent='불러왔습니다.';
  } catch (error) { status.textContent=error.message; }
}
document.querySelector('#reload').addEventListener('click', load);
document.querySelector('#form').addEventListener('submit',async event => {
  event.preventDefault();
  try {
    const next=structuredClone(documentData);
    for (const [name,value] of new FormData(event.target)) {
      const [group,key]=name.split('.'); next[group][key]=group==='costs'?Number(value):value;
    }
    next.z_crystals=JSON.parse(document.querySelector('#crystals').value);
    next.dynamax_excluded=JSON.parse(document.querySelector('#excluded').value);
    const response=await fetch('/api/laboratory-research',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(next)});
    const result=await response.json(); if (!response.ok) throw new Error(result.error || '저장 실패');
    documentData=next; status.textContent='저장했습니다.';
  } catch(error) { status.textContent=error.message; }
});
load();
