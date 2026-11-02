// @vitest-environment jsdom

import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { actions, store } from '../../app/store';
import { chatApi } from '../../services/chatApi';
import { itemApi, type ItemResponse } from '../../services/itemApi';
import {
  requestApi,
  type RequestMediaResponse,
  type RequestResponse,
} from '../../services/requestApi';
import type { User } from '../../types/domain';
import { Messages } from './Messages';

vi.mock('../../services/requestApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/requestApi')>()),
  requestApi: {
    mine: vi.fn(),
    forItem: vi.fn(),
    get: vi.fn(),
    media: vi.fn(),
    cancelTransaction: vi.fn(),
    reportHandoverIssue: vi.fn(),
    getContact: vi.fn(),
    select: vi.fn(),
  },
}));
vi.mock('../../services/itemApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/itemApi')>()),
  itemApi: { mine: vi.fn(), detail: vi.fn() },
}));
vi.mock('../../services/chatApi', () => ({
  chatApi: { list: vi.fn(), send: vi.fn(), pinnedItems: vi.fn() },
}));
vi.mock('../../services/mediaApi', () => ({ mediaApi: { upload: vi.fn() } }));

const DONOR_ID = 9;
const RECEIVER_ID = 12;

const user = (id: number): User => ({
  id: `api_${id}`,
  username: `u${id}`,
  password: '',
  name: `Người dùng ${id}`,
  email: `u${id}@example.com`,
  phone: '0900000000',
  district: 'Quận 1',
  avatarInitials: 'ND',
  totalCredit: 0,
  availableCredit: 0,
  holdCredit: 0,
  rewardPoints: 0,
  reputationStars: 5,
  rank: 'BRONZE',
  status: 'active',
  role: 'user',
  joinedAt: '2026-01-01T00:00:00Z',
  totalTx: 0,
});

const item = (overrides: Partial<ItemResponse> = {}): ItemResponse => ({
  id: 45,
  donorId: DONOR_ID,
  categoryId: 1,
  areaId: 1,
  offerType: 'GIVE',
  title: 'Xe đạp cũ',
  description: 'Xe còn tốt',
  desiredItem: null,
  condition: 'GOOD',
  defectNote: null,
  brand: null,
  images: [],
  defectImages: [],
  operatingStatus: null,
  status: 'RESERVED',
  createdAt: '2026-10-20T08:00:00Z',
  updatedAt: '2026-10-20T08:00:00Z',
  expireAt: null,
  boostedUntil: null,
  ...overrides,
});

const request = (overrides: Partial<RequestResponse> = {}): RequestResponse => ({
  id: 101,
  itemId: 45,
  receiverId: RECEIVER_ID,
  type: 'GIVE',
  status: 'LOGISTICS_CONFIRMED',
  contactRevealedAt: '2026-10-21T08:00:00Z',
  createdAt: '2026-10-20T09:00:00Z',
  itemTitle: 'Xe đạp cũ',