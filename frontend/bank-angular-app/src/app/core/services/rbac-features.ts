import { PERMISSIONS } from './rbac.util';

export type RbacActionKey = 'view' | 'create' | 'edit' | 'execute' | 'decide' | 'manage';
export type RbacPortal = 'bo' | 'ib';

export interface RbacFeatureDef {
  id: string;
  labelKey: string;
  label: string;
  hint?: string;
  actions: Partial<Record<RbacActionKey, string>>;
}

export interface RbacScreenDef {
  id: string;
  portal: RbacPortal;
  labelKey: string;
  label: string;
  icon: string;
  description?: string;
  features: RbacFeatureDef[];
}

/**
 * Hierarchical catalog for both portals:
 * - bo = Back Office (staff)
 * - ib = Internet Banking (customer)
 */
export const RBAC_SCREENS: RbacScreenDef[] = [
  // ─── Internet Banking ───
  {
    id: 'ib-home',
    portal: 'ib',
    labelKey: 'ADMIN.SCR_IB_HOME',
    label: 'IB · Tổng quan',
    icon: 'home',
    description: 'Trang chủ Internet Banking',
    features: [
      {
        id: 'overview',
        labelKey: 'ADMIN.FEAT_IB_HOME_VIEW',
        label: 'Xem tổng quan / số dư',
        hint: 'Menu Tổng quan, widget số dư',
        actions: { view: PERMISSIONS.IB_HOME_VIEW },
      },
    ],
  },
  {
    id: 'ib-accounts',
    portal: 'ib',
    labelKey: 'ADMIN.SCR_IB_ACCOUNTS',
    label: 'IB · Tài khoản',
    icon: 'account_balance_wallet',
    description: 'Tài khoản thanh toán / tiết kiệm',
    features: [
      {
        id: 'list',
        labelKey: 'ADMIN.FEAT_IB_ACC_VIEW',
        label: 'Xem danh sách tài khoản',
        hint: 'Menu Tài khoản, bảng số dư',
        actions: { view: PERMISSIONS.IB_ACCOUNTS_VIEW },
      },
      {
        id: 'open',
        labelKey: 'ADMIN.FEAT_IB_ACC_OPEN',
        label: 'Mở TK PAYMENT / SAVINGS',
        hint: 'Nút mở tài khoản mới',
        actions: { create: PERMISSIONS.IB_ACCOUNTS_OPEN },
      },
    ],
  },
  {
    id: 'ib-transfer',
    portal: 'ib',
    labelKey: 'ADMIN.SCR_IB_TRANSFER',
    label: 'IB · Chuyển tiền',
    icon: 'swap_horiz',
    description: 'Chuyển tiền nội bộ',
    features: [
      {
        id: 'form',
        labelKey: 'ADMIN.FEAT_IB_TX_VIEW',
        label: 'Mở form chuyển tiền',
        hint: 'Menu / nút Chuyển tiền',
        actions: { view: PERMISSIONS.IB_TRANSFER_VIEW },
      },
      {
        id: 'submit',
        labelKey: 'ADMIN.FEAT_IB_TX_EXEC',
        label: 'Thực hiện chuyển tiền',
        hint: 'Nút Xác nhận chuyển',
        actions: { execute: PERMISSIONS.IB_TRANSFER_EXECUTE },
      },
    ],
  },
  {
    id: 'ib-history',
    portal: 'ib',
    labelKey: 'ADMIN.SCR_IB_HISTORY',
    label: 'IB · Lịch sử GD',
    icon: 'receipt_long',
    description: 'Lịch sử chuyển tiền của tôi',
    features: [
      {
        id: 'list',
        labelKey: 'ADMIN.FEAT_IB_HIST_VIEW',
        label: 'Xem lịch sử giao dịch',
        hint: 'Menu Lịch sử GD',
        actions: { view: PERMISSIONS.IB_HISTORY_VIEW },
      },
    ],
  },
  {
    id: 'ib-profile',
    portal: 'ib',
    labelKey: 'ADMIN.SCR_IB_PROFILE',
    label: 'IB · Hồ sơ & MFA',
    icon: 'manage_accounts',
    description: 'Thông tin cá nhân, MFA',
    features: [
      {
        id: 'view',
        labelKey: 'ADMIN.FEAT_IB_PROF_VIEW',
        label: 'Xem hồ sơ',
        hint: 'Menu Hồ sơ',
        actions: { view: PERMISSIONS.IB_PROFILE_VIEW },
      },
      {
        id: 'edit',
        labelKey: 'ADMIN.FEAT_IB_PROF_EDIT',
        label: 'Cập nhật hồ sơ',
        hint: 'Nút Lưu thông tin',
        actions: { edit: PERMISSIONS.IB_PROFILE_EDIT },
      },
      {
        id: 'mfa',
        labelKey: 'ADMIN.FEAT_IB_PROF_MFA',
        label: 'Cài đặt MFA',
        hint: 'Setup / bật TOTP',
        actions: { manage: PERMISSIONS.IB_PROFILE_MFA },
      },
    ],
  },
  {
    id: 'ib-cards',
    portal: 'ib',
    labelKey: 'ADMIN.SCR_IB_CARDS',
    label: 'IB · Thẻ',
    icon: 'credit_card',
    description: 'Thẻ (placeholder)',
    features: [
      {
        id: 'view',
        labelKey: 'ADMIN.FEAT_IB_CARDS_VIEW',
        label: 'Xem module Thẻ',
        actions: { view: PERMISSIONS.IB_CARDS_VIEW },
      },
    ],
  },
  {
    id: 'ib-wealth',
    portal: 'ib',
    labelKey: 'ADMIN.SCR_IB_WEALTH',
    label: 'IB · Đầu tư',
    icon: 'trending_up',
    description: 'Đầu tư / tiết kiệm (placeholder)',
    features: [
      {
        id: 'view',
        labelKey: 'ADMIN.FEAT_IB_WEALTH_VIEW',
        label: 'Xem module Đầu tư',
        actions: { view: PERMISSIONS.IB_WEALTH_VIEW },
      },
    ],
  },
  {
    id: 'ib-support',
    portal: 'ib',
    labelKey: 'ADMIN.SCR_IB_SUPPORT',
    label: 'IB · Hỗ trợ',
    icon: 'support_agent',
    description: 'Ticket hỗ trợ khách hàng — xem riêng / tạo riêng (admin gán qua ma trận)',
    features: [
      {
        id: 'view',
        labelKey: 'ADMIN.FEAT_IB_SUPPORT_VIEW',
        label: 'Xem ticket của tôi',
        hint: 'Menu Hỗ trợ, danh sách & chi tiết ticket (không tạo)',
        actions: { view: PERMISSIONS.IB_SUPPORT_VIEW },
      },
      {
        id: 'create',
        labelKey: 'ADMIN.FEAT_IB_SUPPORT_CREATE',
        label: 'Tạo ticket hỗ trợ',
        hint: 'Form gửi ticket OPEN — admin tick riêng, không gộp với Xem',
        actions: { create: PERMISSIONS.IB_SUPPORT_CREATE },
      },
    ],
  },
  {
    id: 'ib-bills',
    portal: 'ib',
    labelKey: 'ADMIN.SCR_IB_BILLS',
    label: 'IB · Thanh toán hóa đơn',
    icon: 'receipt',
    description: 'Thanh toán hóa đơn điện, nước, internet, truyền hình',
    features: [
      {
        id: 'view',
        labelKey: 'ADMIN.FEAT_IB_BILLS_VIEW',
        label: 'Xem menu & tra cứu hóa đơn',
        hint: 'Menu Thanh toán hóa đơn & tra cứu',
        actions: { view: PERMISSIONS.IB_BILLS_VIEW },
      },
      {
        id: 'execute',
        labelKey: 'ADMIN.FEAT_IB_BILLS_EXEC',
        label: 'Thực hiện thanh toán hóa đơn',
        hint: 'Xác nhận OTP & thanh toán',
        actions: { execute: PERMISSIONS.IB_BILLS_EXECUTE },
      },
    ],
  },

  // ─── Back Office ───
  {
    id: 'bo-dashboard',
    portal: 'bo',
    labelKey: 'ADMIN.SCR_DASHBOARD',
    label: 'BO · Dashboard',
    icon: 'dashboard',
    description: 'Ops overview KPIs',
    features: [
      {
        id: 'overview',
        labelKey: 'ADMIN.FEAT_DASH_OVERVIEW',
        label: 'Xem tổng quan vận hành',
        actions: { view: PERMISSIONS.DASHBOARD_VIEW },
      },
    ],
  },
  {
    id: 'bo-customers',
    portal: 'bo',
    labelKey: 'ADMIN.SCR_CUSTOMERS',
    label: 'BO · Customers',
    icon: 'people',
    description: 'Customer list & KYC',
    features: [
      {
        id: 'list',
        labelKey: 'ADMIN.FEAT_CUS_LIST',
        label: 'Danh sách khách hàng',
        actions: { view: PERMISSIONS.CUSTOMERS_LIST_VIEW },
      },
      {
        id: 'kyc',
        labelKey: 'ADMIN.FEAT_CUS_KYC',
        label: 'Duyệt / từ chối KYC',
        actions: { decide: PERMISSIONS.CUSTOMERS_KYC_DECIDE },
      },
    ],
  },
  {
    id: 'bo-accounts',
    portal: 'bo',
    labelKey: 'ADMIN.SCR_ACCOUNTS',
    label: 'BO · Accounts',
    icon: 'account_balance',
    description: 'Lookup & freeze',
    features: [
      {
        id: 'lookup',
        labelKey: 'ADMIN.FEAT_ACC_LOOKUP',
        label: 'Tra cứu tài khoản',
        actions: { view: PERMISSIONS.ACCOUNTS_LOOKUP_VIEW },
      },
      {
        id: 'freeze',
        labelKey: 'ADMIN.FEAT_ACC_FREEZE',
        label: 'Freeze / Unfreeze',
        actions: { execute: PERMISSIONS.ACCOUNTS_FREEZE_EXECUTE },
      },
      {
        id: 'topup',
        labelKey: 'ADMIN.FEAT_ACC_TOPUP',
        label: 'Nạp tiền tài khoản (Cash-in)',
        actions: { execute: PERMISSIONS.ACCOUNTS_TOPUP_EXECUTE },
      },
      {
        id: 'card-approvals',
        labelKey: 'ADMIN.FEAT_CARDS_APPROVE',
        label: 'Duyệt yêu cầu mở thẻ',
        actions: { execute: PERMISSIONS.CARDS_APPROVE_EXECUTE },
      },
    ],
  },
  {
    id: 'bo-tx',
    portal: 'bo',
    labelKey: 'ADMIN.SCR_TX',
    label: 'BO · Transactions',
    icon: 'swap_horiz',
    description: 'System transfer monitor',
    features: [
      {
        id: 'monitor',
        labelKey: 'ADMIN.FEAT_TX_LIST',
        label: 'Monitor giao dịch',
        actions: { view: PERMISSIONS.TX_LIST_VIEW },
      },
      {
        id: 'report',
        labelKey: 'ADMIN.FEAT_TX_REPORT',
        label: 'Báo cáo giao dịch',
        actions: { view: PERMISSIONS.TX_REPORT_VIEW },
      },
      {
        id: 'recon',
        labelKey: 'ADMIN.FEAT_TX_RECON',
        label: 'Đối soát cuối ngày',
        actions: { view: PERMISSIONS.TX_RECON_VIEW, execute: PERMISSIONS.TX_RECON_EXECUTE },
      },
      {
        id: 'deposits',
        labelKey: 'ADMIN.FEAT_DEPOSITS',
        label: 'Huy động vốn (sổ tiết kiệm)',
        actions: {
          view: PERMISSIONS.DEPOSITS_SUMMARY_VIEW,
          execute: PERMISSIONS.DEPOSITS_BATCH_EXECUTE,
        },
      },
      {
        id: 'deposit-products',
        labelKey: 'ADMIN.FEAT_DEPOSIT_PRODUCTS',
        label: 'Quản lý lãi suất sổ tiết kiệm',
        actions: { manage: PERMISSIONS.DEPOSITS_PRODUCTS_MANAGE },
      },
    ],
  },
  {
    id: 'bo-audit',
    portal: 'bo',
    labelKey: 'ADMIN.SCR_AUDIT',
    label: 'BO · Audit',
    icon: 'history',
    description: 'Audit log',
    features: [
      {
        id: 'log',
        labelKey: 'ADMIN.FEAT_AUDIT_LIST',
        label: 'Xem audit log',
        actions: { view: PERMISSIONS.AUDIT_LIST_VIEW },
      },
    ],
  },
  {
    id: 'bo-rbac',
    portal: 'bo',
    labelKey: 'ADMIN.SCR_RBAC',
    label: 'BO · Phân quyền',
    icon: 'admin_panel_settings',
    description: 'Roles & assignment',
    features: [
      {
        id: 'access',
        labelKey: 'ADMIN.FEAT_RBAC_ACCESS',
        label: 'Mở module RBAC',
        actions: { view: PERMISSIONS.RBAC_ACCESS },
      },
      {
        id: 'users',
        labelKey: 'ADMIN.FEAT_RBAC_USERS',
        label: 'Gán role cho user',
        actions: { manage: PERMISSIONS.RBAC_USERS_ASSIGN },
      },
      {
        id: 'roles',
        labelKey: 'ADMIN.FEAT_RBAC_ROLES',
        label: 'Tạo / sửa role & matrix',
        actions: { manage: PERMISSIONS.RBAC_ROLES_MANAGE },
      },
    ],
  },
  {
    id: 'bo-risk',
    portal: 'bo',
    labelKey: 'ADMIN.SCR_RISK',
    label: 'BO · Risk',
    icon: 'shield',
    description: 'Risk & compliance',
    features: [
      {
        id: 'module',
        labelKey: 'ADMIN.FEAT_RISK_VIEW',
        label: 'Mở module Risk',
        actions: { view: PERMISSIONS.RISK_VIEW },
      },
    ],
  },
  {
    id: 'bo-users',
    portal: 'bo',
    labelKey: 'ADMIN.SCR_USERS',
    label: 'BO · Quản lý người dùng',
    icon: 'manage_accounts',
    description: 'Danh sách user · khóa · cấp lại MK',
    features: [
      {
        id: 'reset',
        labelKey: 'ADMIN.FEAT_PWD_RESET',
        label: 'Cấp lại mật khẩu (blind)',
        hint: 'Icon password trên dòng user — admin không xem MK tạm',
        actions: { execute: PERMISSIONS.USERS_PASSWORD_RESET },
      },
      {
        id: 'lock',
        labelKey: 'ADMIN.FEAT_USER_LOCK',
        label: 'Khóa / mở khóa đăng nhập',
        hint: 'Icon lock trên dòng user',
        actions: { execute: PERMISSIONS.USERS_LOCK_EXECUTE },
      },
    ],
  },
  {
    id: 'bo-support-tickets',
    portal: 'bo',
    labelKey: 'ADMIN.SCR_SUPPORT_TICKETS',
    label: 'BO · Ticket hỗ trợ',
    icon: 'support_agent',
    description:
      'Queue ticket CS — staff duyệt/từ chối trực tiếp. Claim (nhận xử lý) tùy chọn. Gán permission theo role do admin.',
    features: [
      {
        id: 'list',
        labelKey: 'ADMIN.FEAT_SUPPORT_TICKETS_LIST',
        label: 'Xem / tìm queue ticket',
        hint: 'Menu Ticket hỗ trợ, danh sách & chi tiết',
        actions: { view: PERMISSIONS.SUPPORT_TICKETS_LIST },
      },
      {
        id: 'claim',
        labelKey: 'ADMIN.FEAT_SUPPORT_TICKETS_CLAIM',
        label: 'Nhận xử lý ticket (tuỳ chọn)',
        hint: 'OPEN → IN_PROGRESS + gán người xử lý. Không bắt buộc trước khi duyệt.',
        actions: { execute: PERMISSIONS.SUPPORT_TICKETS_CLAIM },
      },
      {
        id: 'decide',
        labelKey: 'ADMIN.FEAT_SUPPORT_TICKETS_DECIDE',
        label: 'Duyệt / từ chối ticket',
        hint: 'Resolve/reject OPEN hoặc IN_PROGRESS; cùng người nhận xử lý vẫn được duyệt.',
        actions: { decide: PERMISSIONS.SUPPORT_TICKETS_DECIDE },
      },
    ],
  },
];

export const RBAC_ACTION_COLUMNS: { key: RbacActionKey; labelKey: string; label: string }[] = [
  { key: 'view', labelKey: 'ADMIN.ACT_VIEW', label: 'Xem' },
  { key: 'create', labelKey: 'ADMIN.ACT_CREATE', label: 'Tạo' },
  { key: 'edit', labelKey: 'ADMIN.ACT_EDIT', label: 'Sửa' },
  { key: 'decide', labelKey: 'ADMIN.ACT_DECIDE', label: 'Duyệt' },
  { key: 'execute', labelKey: 'ADMIN.ACT_EXECUTE', label: 'Thực thi' },
  { key: 'manage', labelKey: 'ADMIN.ACT_MANAGE', label: 'Quản trị' },
];

export function screensByPortal(portal: RbacPortal): RbacScreenDef[] {
  return RBAC_SCREENS.filter((s) => s.portal === portal);
}
