(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.RadioLibraryCache=api;})(typeof globalThis!=='undefined'?globalThis:this,()=>{
 'use strict';
 function create(key){
  let cached=null;
  function get(stations){
   // Mutations may replace the array, replace a row, or edit a row in place.
   // Compare cheap fields once per lookup; canonicalize URLs only when rebuilding.
   if(cached&&cached.source===stations&&cached.snapshot.length===stations.length){
    let unchanged=true;
    for(let i=0;i<stations.length;i++){const x=stations[i],old=cached.snapshot[i];if(old.item!==x||old.url!==x.url||old.country!==x.country||old.region!==x.region){unchanged=false;break;}}
    if(unchanged)return cached;
   }
   const indices=new Map(),byRegion=new Map([['ALL',stations]]),byCountry=new Map(),counts=new Map(),countryByURL=new Map(),snapshot=[],groups=new Map();
   const digits=Math.max(3,String(stations.length).length);
   for(let i=0;i<stations.length;i++){
    const x=stations[i],id=key(x);if(!indices.has(id))indices.set(id,String(i+1).padStart(digits,'0'));
    snapshot.push({item:x,url:x.url,country:x.country,region:x.region});
    if(!countryByURL.has(x.url))countryByURL.set(x.url,x.country||'');
    if(!byRegion.has(x.region))byRegion.set(x.region,[]);byRegion.get(x.region).push(x);
   }
   const countryOf=x=>x?.country||(['TW','JP','KR'].includes(x?.region)?x.region:countryByURL.get(x?.url)||'');
   const international=byRegion.get('INT')||[];
   for(const x of stations){const country=countryOf(x);if(!byCountry.has(country))byCountry.set(country,[]);byCountry.get(country).push(x);counts.set(country,(counts.get(country)||0)+1);}
   cached={source:stations,snapshot,indices,byRegion,byCountry,counts,international,countryOf,groups};return cached;
  }
  return {get};
 }
 return {create};
});
