// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import type { Item } from '../../types/domain';
import { HomeLifecycle } from './HomeCommunity';

const makeItem = (index: number, images = [`https://example.com/item-${index}.jpg`]): Item => ({
  id: String(index),
  ownerId: '9',
  title: `Món đồ ${index}`,
  description: 'Mô tả',
  type: 'gift',
  category: 'Sách',
  condition: 'good',
  district: 'Bình Thạnh',
  images,
  status: 'approved',
  postedAt: '2026-10-20T08:00:00Z',
});

afterEach(cleanup);

describe('HomeLifecycle', () => {
  it('hiển thị tối đa 3 ảnh thật theo đúng thứ tự được truyền vào', () => {
    render(
      <MemoryRouter>
        <HomeLifecycle items={[makeItem(1), makeItem(2), makeItem(3), makeItem(4)]} />
      </MemoryRouter>,
    );

    const images = screen.getAllByRole('img');
    expect(images).toHaveLength(3);
    expect(images.map((image) => image.getAttribute('src'))).toEqual([
      'https://example.com/item-1.jpg',
      'https://example.com/item-2.jpg',
      'https://example.com/item-3.jpg',
    ]);
    expect(images.map((image) => image.className)).toEqual([
      'home-lifecycle-collage-image home-lifecycle-collage-image-1',
      'home-lifecycle-collage-image home-lifecycle-collage-image-2',
      'home-lifecycle-collage-image home-lifecycle-collage-image-3',
    ]);
  });

  it('không thêm ảnh thay thế khi có ít hơn 3 bài có ảnh', () => {
    render(
      <MemoryRouter>
        <HomeLifecycle items={[makeItem(1), makeItem(2, [])]} />
      </MemoryRouter>,
    );

    expect(screen.getAllByRole('img')).toHaveLength(1);
    expect(screen.getByRole('img', { name: 'Món đồ 1' })).toBeTruthy();
  });
});
