function s2Can(permission) { return (currentUser.permissions || []).includes(permission); }
async function s2Api(path,options={}) {
  const response=await fetch(`api/${path}`,{cache:'no-store',...options});
  const data=await response.json().catch(()=>({message:'Máy chủ trả về dữ liệu không hợp lệ'}));
  if(!response.ok)throw new Error(response.status===401?'Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.':data.message || 'Không thể xử lý yêu cầu');
  return data;
}
function s2Json(method,data){return {method,headers:{'Content-Type':'application/json'},body:JSON.stringify(data)};}
function s2Error(error){showToast(error.message || 'Không thể xử lý yêu cầu');}
const s2Money=value=>value==null?'—':new Intl.NumberFormat('vi-VN',{style:'currency',currency:'VND'}).format(value);
