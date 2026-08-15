-- Generation ke claim ko ek waqt deta hai, taaki woh expire ho sake.
--
-- Iske bina claim hamesha ke liye hai: process generation ke beech mar jaye to project
-- GENERATING mein atak jata hai aur uske baad har generate 409 deta hai. Woh project
-- permanently unusable ho jata hai, aur ise theek karne ka koi API raasta nahi tha.
--
-- Nullable, aur jaan-boojh ke bina default: purani rows ke liye null ka matlab "claim ka
-- waqt maloom nahi", jise stale maana jata hai. Isse woh projects jo abhi GENERATING mein
-- atke hain, khud hi claimable ho jate hain — inhe theek karne ke liye alag script nahi chahiye.
--
-- updated_at ko lease banane ka aasan raasta bhi tha, par woh kisi bhi update pe badalta
-- hai. Project ka naam badalne se generation ka lease badhna galat hai, chahe nuksaan na ho
alter table projects
    add column generation_started_at timestamp(6) with time zone;
