const loginView = document.getElementById('loginView');
const registerView = document.getElementById('registerView');
const forgotPasswordView = document.getElementById('forgotPasswordView');
const resetPasswordView = document.getElementById('resetPasswordView');
const appView = document.getElementById('appView');
const loginForm = document.getElementById('loginForm');
const loginError = document.getElementById('loginError');
const emailInput = document.getElementById('email');
const passwordInput = document.getElementById('password');
const dashboardContent = document.getElementById('dashboardContent');
const moduleContent = document.getElementById('moduleContent');
let toastTimer;

const roleConfigs = {
  CUSTOMER: { label: 'Đại lý', menu: ['Tổng quan', 'Sản phẩm & bảng giá'] },
  SALES_REP: { label: 'Nhân viên kinh doanh', menu: ['Tổng quan', 'Sản phẩm & bảng giá'] },
  SALES_MANAGER: { label: 'Quản lý kinh doanh', menu: ['Tổng quan', 'Sản phẩm & bảng giá'] },
  WAREHOUSE: { label: 'Nhân viên kho', menu: ['Tổng quan'] },
  WH_MANAGER: { label: 'Quản lý kho', menu: ['Tổng quan'] },
  ACCOUNTANT: { label: 'Kế toán công nợ', menu: ['Tổng quan', 'Sản phẩm & bảng giá'] },
  ADMIN: {
    label: 'Quản trị hệ thống',
    menu: ['Tổng quan', 'Người dùng', 'Danh mục dùng chung', 'Nhật ký hệ thống']
  }
};
let activeRole = 'WH_MANAGER';

const formSchemas = {
  users: [
    ['username', 'Tên đăng nhập', 'text', true],
    ['fullName', 'Họ và tên', 'text', true],
    ['email', 'Email', 'email', true],
    ['phone', 'Số điện thoại', 'tel', true],
    ['role', 'Vai trò', 'select', true, Object.values(roleConfigs).map((item) => item.label)],
    ['scope', 'Kho / địa bàn', 'text', true],
    ['status', 'Trạng thái', 'select', true, ['Hoạt động', 'Tạm khóa']]
  ]
};
let currentUser = {};
let viewRevision = 0;
