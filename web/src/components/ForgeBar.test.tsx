import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import type { Link } from '../api/types';
import { ForgeBar } from './ForgeBar';

const link: Link = {
  code: '4C92',
  shortUrl: 'http://localhost:8080/4C92',
  longUrl: 'https://example.com/a/long/path',
  createdAt: '2026-08-15T02:30:51Z',
};

describe('ForgeBar', () => {
  it('shows the short link after forging', async () => {
    const onForge = vi.fn().mockResolvedValue(link);
    render(<ForgeBar onForge={onForge} />);

    await userEvent.type(screen.getByLabelText(/long url/i), 'https://example.com/a/long/path');
    await userEvent.click(screen.getByRole('button', { name: /forge link/i }));

    expect(onForge).toHaveBeenCalledWith('https://example.com/a/long/path');
    expect(await screen.findByText('localhost:8080/4C92')).toBeInTheDocument();
  });

  it('clears the input so the next URL can be pasted straight in', async () => {
    render(<ForgeBar onForge={vi.fn().mockResolvedValue(link)} />);
    const input = screen.getByLabelText(/long url/i);

    await userEvent.type(input, 'https://example.com/a/long/path');
    await userEvent.click(screen.getByRole('button', { name: /forge link/i }));

    await waitFor(() => expect(input).toHaveValue(''));
  });

  /** The server rejects unsupported schemes; the reason has to reach the person who typed it. */
  it("surfaces the server's rejection message", async () => {
    const onForge = vi
      .fn()
      .mockRejectedValue(new ApiError(400, 'url scheme must be http or https, got javascript'));
    render(<ForgeBar onForge={onForge} />);

    await userEvent.type(screen.getByLabelText(/long url/i), 'https://example.com');
    await userEvent.click(screen.getByRole('button', { name: /forge link/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/scheme must be http or https/i);
  });

  it('does not submit an empty form', () => {
    render(<ForgeBar onForge={vi.fn()} />);
    expect(screen.getByRole('button', { name: /forge link/i })).toBeDisabled();
  });
});
