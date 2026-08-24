import type { CSSProperties, ReactNode } from 'react'
import { Input } from 'tdesign-react'
import type { InputProps } from 'tdesign-react'

const fieldStyle: CSSProperties = {
  rowGap: '8px',
  width: '100%',
}

export function AuthInputField({
  label,
  ...inputProps
}: InputProps & { label: string }) {
  return (
    <label className="form-field" style={fieldStyle}>
      <span>{label}</span>
      <Input {...inputProps} />
    </label>
  )
}

export function VerificationCodeField({
  action,
  label,
  ...inputProps
}: InputProps & { action: ReactNode; label: string }) {
  return (
    <div className="form-field" style={fieldStyle}>
      <span aria-hidden="true">{label}</span>
      <div className="code-row">
        <label className="verification-code-input">
          <span className="field-accessible-label">{label}</span>
          <Input {...inputProps} />
        </label>
        {action}
      </div>
    </div>
  )
}
